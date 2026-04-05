package com.m13.egghunt.models;

import org.bukkit.Location;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An admin-placed egg. Single Nexo furniture entity.
 * ProtocolLib handles per-player visual swapping.
 */
public final class AdminEgg {

    private final UUID entityUuid;
    private final int entityId;
    private final String tierKey;
    private final String nexoId;
    private final Location location;
    private final long placedAt;
    private final Set<UUID> claimedBy;

    public AdminEgg(UUID entityUuid, int entityId, String tierKey, String nexoId,
                    Location location, long placedAt) {
        this.entityUuid = entityUuid;
        this.entityId = entityId;
        this.tierKey = tierKey;
        this.nexoId = nexoId;
        this.location = location;
        this.placedAt = placedAt;
        this.claimedBy = ConcurrentHashMap.newKeySet();
    }

    public AdminEgg(UUID entityUuid, int entityId, String tierKey, String nexoId,
                    Location location, long placedAt, Set<UUID> claimedBy) {
        this.entityUuid = entityUuid;
        this.entityId = entityId;
        this.tierKey = tierKey;
        this.nexoId = nexoId;
        this.location = location;
        this.placedAt = placedAt;
        this.claimedBy = ConcurrentHashMap.newKeySet();
        this.claimedBy.addAll(claimedBy);
    }

    public boolean hasClaimed(UUID playerUuid) { return claimedBy.contains(playerUuid); }
    public void markClaimed(UUID playerUuid) { claimedBy.add(playerUuid); }

    public UUID entityUuid() { return entityUuid; }
    public int entityId() { return entityId; }
    public String tierKey() { return tierKey; }
    public String nexoId() { return nexoId; }
    public Location location() { return location; }
    public long placedAt() { return placedAt; }
    public Set<UUID> claimedBy() { return claimedBy; }
}
