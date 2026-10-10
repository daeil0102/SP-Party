package net.teujaem.spparty.service;

import net.teujaem.spFramework.api.ProxyData;
import net.teujaem.spFramework.api.event.ProxyEvent;
import net.teujaem.spparty.Main;
import net.teujaem.spparty.model.Party;
import net.teujaem.spparty.util.PayloadCodec;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PartyService implements Listener {
    public static final String CHANNEL = "SP-Party";
    private static final long INVITE_TTL = 5 * 60 * 1000L;
    private static final String PREFIX = "§b[파티] §f";

    public record Invite(UUID partyId, UUID leaderId, String leaderName, String targetName, long expiresAt) {}

    private final Main plugin;
    private final boolean proxyEnabled;
    private final int maxSize;
    private final Map<UUID, Party> parties = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> memberParty = new ConcurrentHashMap<>();
    private final Map<String, Invite> invites = new ConcurrentHashMap<>();
    private final Set<String> seenMessages = ConcurrentHashMap.newKeySet();
    private final Set<UUID> partyChatPlayers = ConcurrentHashMap.newKeySet();

    public PartyService(Main plugin, boolean proxyEnabled, int maxSize) {
        this.plugin = plugin;
        this.proxyEnabled = proxyEnabled;
        this.maxSize = Math.max(2, Math.min(100, maxSize));
    }

    public Party create(Player player) {
        requireNoParty(player.getUniqueId());
        Party party = new Party(UUID.randomUUID(), player.getUniqueId(), player.getName());
        applyParty(party);
        publish("party-state", partyPayload(party));
        return party;
    }

    public void invite(Player leader, String targetName) {
        Party party = requireParty(leader.getUniqueId());
        requireLeader(party, leader.getUniqueId());
        if (party.members().size() >= maxSize) throw new IllegalStateException("파티가 가득 찼습니다.");
        if (leader.getName().equalsIgnoreCase(targetName)) throw new IllegalStateException("자기 자신은 초대할 수 없습니다.");
        if (party.members().values().stream().anyMatch(name -> name.equalsIgnoreCase(targetName)))
            throw new IllegalStateException("이미 파티에 참가한 플레이어입니다.");
        Invite invite = new Invite(party.id(), leader.getUniqueId(), leader.getName(), targetName, System.currentTimeMillis() + INVITE_TTL);
        invites.put(targetName.toLowerCase(Locale.ROOT), invite);
        publish("party-invite", Map.of("partyId", party.id().toString(), "leaderId", leader.getUniqueId().toString(),
                "leaderName", leader.getName(), "targetName", targetName, "expiresAt", String.valueOf(invite.expiresAt())));
    }

    public Party accept(Player player, String leaderName) {
        requireNoParty(player.getUniqueId());
        Invite invite = validInvite(player.getName(), leaderName);
        Party party = parties.get(invite.partyId());
        if (party == null) throw new IllegalStateException("파티 정보를 찾지 못했습니다. 잠시 후 다시 시도하세요.");
        if (party.members().size() >= maxSize) throw new IllegalStateException("파티가 가득 찼습니다.");
        party.members().put(player.getUniqueId(), player.getName());
        invites.remove(player.getName().toLowerCase(Locale.ROOT));
        applyParty(party);
        publish("party-state", partyPayload(party));
        publishNotification(party, "§e" + player.getName() + "§f님이 파티에 참가했습니다.");
        return party;
    }

    public void deny(Player player, String leaderName) {
        Invite invite = validInvite(player.getName(), leaderName);
        invites.remove(player.getName().toLowerCase(Locale.ROOT));
        publish("party-notify", Map.of("target", invite.leaderId().toString(), "message", "§e" + player.getName() + "§f님이 파티 초대를 거절했습니다."));
    }

    public void leave(Player player) {
        Party party = requireParty(player.getUniqueId());
        if (party.leader().equals(player.getUniqueId())) {
            if (party.members().size() == 1) { disband(player); return; }
            UUID next = party.members().keySet().stream().filter(id -> !id.equals(player.getUniqueId())).findFirst().orElseThrow();
            party.leader(next);
        }
        party.members().remove(player.getUniqueId());
        memberParty.remove(player.getUniqueId());
        partyChatPlayers.remove(player.getUniqueId());
        applyParty(party);
        publish("party-state", partyPayload(party));
        publish("party-notify", Map.of("target", player.getUniqueId().toString(), "message", "파티에서 탈퇴했습니다."));
        publishNotification(party, "§e" + player.getName() + "§f님이 파티에서 나갔습니다.");
    }

    public void kick(Player leader, String targetName) {
        Party party = requireParty(leader.getUniqueId());
        requireLeader(party, leader.getUniqueId());
        UUID target = memberByName(party, targetName);
        if (target.equals(leader.getUniqueId())) throw new IllegalStateException("파티장은 자신을 강퇴할 수 없습니다.");
        String name = party.name(target);
        party.members().remove(target);
        memberParty.remove(target);
        partyChatPlayers.remove(target);
        applyParty(party);
        publish("party-state", partyPayload(party));
        publish("party-notify", Map.of("target", target.toString(), "message", "파티에서 강퇴되었습니다."));
        publishNotification(party, "§e" + name + "§f님이 파티에서 제외되었습니다.");
    }

    public void promote(Player leader, String targetName) {
        Party party = requireParty(leader.getUniqueId());
        requireLeader(party, leader.getUniqueId());
        UUID target = memberByName(party, targetName);
        party.leader(target);
        applyParty(party);
        publish("party-state", partyPayload(party));
        publishNotification(party, "§e" + party.name(target) + "§f님이 새 파티장이 되었습니다.");
    }

    public void disband(Player leader) {
        Party party = requireParty(leader.getUniqueId());
        requireLeader(party, leader.getUniqueId());
        parties.remove(party.id());
        party.members().keySet().forEach(memberParty::remove);
        party.members().keySet().forEach(partyChatPlayers::remove);
        publish("party-remove", Map.of("partyId", party.id().toString()));
        publishNotification(party, "파티가 해산되었습니다.");
    }

    public boolean togglePartyChat(Player player) {
        return setPartyChat(player, !partyChatPlayers.contains(player.getUniqueId()));
    }

    public boolean setPartyChat(Player player, boolean enabled) {
        if (enabled) {
            requireParty(player.getUniqueId());
            partyChatPlayers.add(player.getUniqueId());
        } else {
            partyChatPlayers.remove(player.getUniqueId());
        }
        return enabled;
    }

    public boolean isPartyChatEnabled(UUID playerId) {
        return partyChatPlayers.contains(playerId) && memberParty.containsKey(playerId);
    }

    public void chat(Player sender, String message) {
        if (message.isBlank()) throw new IllegalStateException("메시지를 입력하세요.");
        Party party = requireParty(sender.getUniqueId());
        publish("party-chat", Map.of("partyId", party.id().toString(), "sender", sender.getName(), "message", message));
    }

    public List<String> describe(Player player) {
        Party party = requireParty(player.getUniqueId());
        List<String> lines = new ArrayList<>();
        lines.add("§8§m---------------- §bSP-Party §8§m----------------");
        lines.add("§f파티장: §e" + party.name(party.leader()));
        lines.add("§f인원: §a" + party.members().size() + "§7/§a" + maxSize);
        party.members().forEach((uuid, name) -> lines.add((uuid.equals(party.leader()) ? "§6★ " : "§7• ") + "§f" + name));
        lines.add("§8§m--------------------------------------------");
        return lines;
    }

    public List<String> memberNames(Player player) {
        Party party = parties.get(memberParty.get(player.getUniqueId()));
        return party == null ? List.of() : new ArrayList<>(party.members().values());
    }

    public void requestSnapshot() { publish("snapshot-request", Map.of()); }

    public void notifyPending(Player player) {
        cleanupInvites();
        Invite invite = invites.get(player.getName().toLowerCase(Locale.ROOT));
        if (invite != null) player.sendMessage(PREFIX + "§e" + invite.leaderName() + "§f님의 파티 초대가 있습니다. §7/파티 수락 " + invite.leaderName());
    }

    @EventHandler
    public void onProxyEvent(ProxyEvent event) {
        if (!CHANNEL.equalsIgnoreCase(event.getPluginName())) return;
        Map<String, String> values = PayloadCodec.decode(String.valueOf(event.getValue()));
        String messageId = values.getOrDefault("messageId", "");
        if (!messageId.isBlank() && !seenMessages.add(messageId)) return;
        handleEvent(event.getEventName(), values);
    }

    private void publish(String event, Map<String, String> source) {
        Map<String, String> values = new LinkedHashMap<>(source);
        String messageId = UUID.randomUUID().toString();
        values.put("messageId", messageId);
        seenMessages.add(messageId);
        handleEvent(event, values);
        if (!proxyEnabled) return;
        try { ProxyData.sendToServer(CHANNEL, event, PayloadCodec.encode(values)); }
        catch (Exception | LinkageError exception) { plugin.getLogger().warning("ProxyData 전송 실패, 로컬 파티 기능은 계속 동작합니다: " + exception.getMessage()); }
    }

    private void handleEvent(String event, Map<String, String> values) {
        try {
            switch (event.toLowerCase(Locale.ROOT)) {
                case "party-state" -> applyParty(parseParty(values));
                case "party-remove" -> removeParty(UUID.fromString(values.get("partyId")));
                case "party-invite" -> applyInvite(values);
                case "party-chat" -> deliverChat(values);
                case "party-notify" -> notifyLocal(values.get("target"), values.get("message"));
                case "snapshot-request" -> parties.values().forEach(party -> publish("party-state", partyPayload(party)));
                default -> { }
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("파티 동기화 데이터 처리 실패: " + exception.getMessage());
        }
    }

    private void applyInvite(Map<String, String> values) {
        Invite invite = new Invite(UUID.fromString(values.get("partyId")), UUID.fromString(values.get("leaderId")),
                values.get("leaderName"), values.get("targetName"), Long.parseLong(values.get("expiresAt")));
        invites.put(invite.targetName().toLowerCase(Locale.ROOT), invite);
        Player target = Bukkit.getPlayerExact(invite.targetName());
        if (target != null) target.sendMessage(PREFIX + "§e" + invite.leaderName() + "§f님이 파티에 초대했습니다. §7/파티 수락 " + invite.leaderName());
    }

    private void deliverChat(Map<String, String> values) {
        Party party = parties.get(UUID.fromString(values.get("partyId")));
        if (party == null) return;
        String formatted = "§b[파티] §e" + values.get("sender") + "§f: " + values.get("message");
        party.members().keySet().forEach(uuid -> { Player player = Bukkit.getPlayer(uuid); if (player != null) player.sendMessage(formatted); });
    }

    private void publishNotification(Party party, String message) {
        party.members().keySet().forEach(uuid -> publish("party-notify", Map.of("target", uuid.toString(), "message", message)));
    }

    private void notifyLocal(String target, String message) {
        if (target == null) return;
        Player player = Bukkit.getPlayer(UUID.fromString(target));
        if (player != null) player.sendMessage(PREFIX + message);
    }

    private Map<String, String> partyPayload(Party party) {
        StringBuilder members = new StringBuilder();
        party.members().forEach((uuid, name) -> {
            if (members.length() > 0) members.append(';');
            members.append(uuid).append(',').append(name);
        });
        return Map.of("partyId", party.id().toString(), "leaderId", party.leader().toString(), "members", members.toString());
    }

    private Party parseParty(Map<String, String> values) {
        UUID id = UUID.fromString(values.get("partyId"));
        UUID leader = UUID.fromString(values.get("leaderId"));
        LinkedHashMap<UUID, String> members = new LinkedHashMap<>();
        for (String item : values.getOrDefault("members", "").split(";")) {
            String[] fields = item.split(",", 2);
            if (fields.length == 2) members.put(UUID.fromString(fields[0]), fields[1]);
        }
        String leaderName = members.getOrDefault(leader, "Leader");
        Party party = new Party(id, leader, leaderName);
        party.members().clear();
        party.members().putAll(members);
        return party;
    }

    private void applyParty(Party party) {
        Party previous = parties.put(party.id(), party);
        if (previous != null) {
            previous.members().keySet().forEach(uuid -> memberParty.remove(uuid, party.id()));
            previous.members().keySet().stream()
                    .filter(uuid -> !party.members().containsKey(uuid))
                    .forEach(partyChatPlayers::remove);
        }
        party.members().keySet().forEach(uuid -> memberParty.put(uuid, party.id()));
    }

    private void removeParty(UUID partyId) {
        Party removed = parties.remove(partyId);
        if (removed != null) removed.members().keySet().forEach(uuid -> { memberParty.remove(uuid, partyId); partyChatPlayers.remove(uuid); });
    }

    private Invite validInvite(String targetName, String leaderName) {
        cleanupInvites();
        Invite invite = invites.get(targetName.toLowerCase(Locale.ROOT));
        if (invite == null || !invite.leaderName().equalsIgnoreCase(leaderName)) throw new IllegalStateException("유효한 파티 초대가 없습니다.");
        return invite;
    }

    private void cleanupInvites() { invites.values().removeIf(invite -> invite.expiresAt() < System.currentTimeMillis()); }
    private void requireNoParty(UUID player) { if (memberParty.containsKey(player)) throw new IllegalStateException("이미 파티에 참가 중입니다."); }
    private Party requireParty(UUID player) { Party party = parties.get(memberParty.get(player)); if (party == null) throw new IllegalStateException("참가 중인 파티가 없습니다."); return party; }
    private void requireLeader(Party party, UUID player) { if (!party.leader().equals(player)) throw new IllegalStateException("파티장만 사용할 수 있습니다."); }
    private UUID memberByName(Party party, String name) { return party.members().entrySet().stream().filter(entry -> entry.getValue().equalsIgnoreCase(name)).map(Map.Entry::getKey).findFirst().orElseThrow(() -> new IllegalStateException("파티원을 찾을 수 없습니다.")); }

    public void shutdown() {
        parties.clear();
        memberParty.clear();
        invites.clear();
        seenMessages.clear();
        partyChatPlayers.clear();
    }
}
