package net.teujaem.spparty.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class Party {
    private final UUID id;
    private UUID leader;
    private final LinkedHashMap<UUID, String> members = new LinkedHashMap<>();

    public Party(UUID id, UUID leader, String leaderName) {
        this.id = id;
        this.leader = leader;
        members.put(leader, leaderName);
    }

    public UUID id() { return id; }
    public UUID leader() { return leader; }
    public void leader(UUID leader) { this.leader = leader; }
    public Map<UUID, String> members() { return members; }
    public String name(UUID uuid) { return members.getOrDefault(uuid, uuid.toString()); }
}
