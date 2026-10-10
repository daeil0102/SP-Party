package net.teujaem.spparty.command;

import net.teujaem.spparty.Main;
import net.teujaem.spparty.service.PartyService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class PartyCommand implements CommandExecutor, TabCompleter {
    private static final String PREFIX = "§b[파티] §f";
    private final Main plugin;
    private final PartyService service;

    public PartyCommand(Main plugin, PartyService service) { this.plugin = plugin; this.service = service; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("플레이어만 사용할 수 있습니다."); return true; }
        try {
            if (command.getName().equalsIgnoreCase("pc")) {
                if (args.length == 0) showChatMode(player, service.togglePartyChat(player));
                else service.chat(player, String.join(" ", args));
                return true;
            }
            if (args.length == 0 || matches(args[0], "help", "도움말")) { help(player); return true; }
            String action = args[0].toLowerCase(Locale.ROOT);
            switch (action) {
                case "create", "생성" -> { service.create(player); player.sendMessage(PREFIX + "파티를 생성했습니다."); }
                case "invite", "초대" -> { requireArg(args); service.invite(player, args[1]); player.sendMessage(PREFIX + "§e" + args[1] + "§f님을 초대했습니다."); }
                case "accept", "수락" -> { requireArg(args); service.accept(player, args[1]); player.sendMessage(PREFIX + "파티에 참가했습니다."); }
                case "deny", "거절" -> { requireArg(args); service.deny(player, args[1]); player.sendMessage(PREFIX + "파티 초대를 거절했습니다."); }
                case "leave", "탈퇴" -> service.leave(player);
                case "kick", "강퇴" -> { requireArg(args); service.kick(player, args[1]); }
                case "promote", "위임" -> { requireArg(args); service.promote(player, args[1]); }
                case "disband", "해산" -> service.disband(player);
                case "list", "목록" -> service.describe(player).forEach(player::sendMessage);
                case "chat", "채팅" -> {
                    if (args.length == 1) showChatMode(player, service.togglePartyChat(player));
                    else service.chat(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                }
                case "partychat", "파티채팅" -> showChatMode(player, service.setPartyChat(player, true));
                case "globalchat", "전체채팅" -> showChatMode(player, service.setPartyChat(player, false));
                default -> help(player);
            }
        } catch (IllegalStateException exception) {
            player.sendMessage(PREFIX + "§c" + exception.getMessage());
        } catch (Exception | LinkageError exception) {
            plugin.getLogger().warning("파티 명령 처리 실패: " + exception.getMessage());
            player.sendMessage(PREFIX + "§c명령 처리 중 오류가 발생했습니다.");
        }
        return true;
    }

    private void showChatMode(Player player, boolean partyChat) {
        player.sendMessage(PREFIX + (partyChat
                ? "채팅 모드를 §b파티 채팅§f으로 변경했습니다."
                : "채팅 모드를 §a전체 채팅§f으로 변경했습니다."));
    }

    private void requireArg(String[] args) { if (args.length < 2) throw new IllegalStateException("플레이어 이름을 입력하세요."); }
    private boolean matches(String value, String... candidates) { return Arrays.stream(candidates).anyMatch(value::equalsIgnoreCase); }

    private void help(Player player) {
        player.sendMessage("§8§m---------------- §bSP-Party §8§m----------------");
        player.sendMessage("§f/파티 생성 §7- 새 파티 생성");
        player.sendMessage("§f/파티 초대 <닉네임> §7- 플레이어 초대");
        player.sendMessage("§f/파티 수락 <파티장> §7- 초대 수락");
        player.sendMessage("§f/파티 거절 <파티장> §7- 초대 거절");
        player.sendMessage("§f/파티 목록 §7- 파티원 확인");
        player.sendMessage("§f/파티 채팅 §7- 전체/파티 채팅 모드 전환 (§f/pc§7)");
        player.sendMessage("§f/파티 파티채팅 §7- 파티 채팅 모드");
        player.sendMessage("§f/파티 전체채팅 §7- 전체 채팅 모드");
        player.sendMessage("§f/파티 채팅 <메시지> §7- 한 번만 파티 채팅 전송");
        player.sendMessage("§f/파티 강퇴 <닉네임> §7- 파티원 강퇴");
        player.sendMessage("§f/파티 위임 <닉네임> §7- 파티장 위임");
        player.sendMessage("§f/파티 탈퇴 §7- 파티 나가기");
        player.sendMessage("§f/파티 해산 §7- 파티 해산");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player) || command.getName().equalsIgnoreCase("pc")) return List.of();
        if (args.length == 1) return filter(List.of("생성", "초대", "수락", "거절", "목록", "채팅", "파티채팅", "전체채팅", "강퇴", "위임", "탈퇴", "해산"), args[0]);
        if (args.length == 2 && matches(args[0], "invite", "초대")) return filter(plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        if (args.length == 2 && (matches(args[0], "kick", "강퇴") || matches(args[0], "promote", "위임"))) return filter(service.memberNames(player), args[1]);
        return List.of();
    }

    private List<String> filter(List<String> values, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) if (value.toLowerCase(Locale.ROOT).startsWith(prefix)) result.add(value);
        return result;
    }
}
