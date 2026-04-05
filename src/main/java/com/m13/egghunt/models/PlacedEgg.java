package com.m13.egghunt.models;

import org.bukkit.Location;

import java.util.UUID;

/**
 * A player-spawned egg (invisible chicken). Single-use, despawns on claim.
 */
public record PlacedEgg(
        UUID entityUuid,
        String tierKey,
        Location location,
        long placedAt
) {}
