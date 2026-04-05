package com.m13.egghunt.models;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * An egg tier. nexoIds is a list so each tier can have visual variety.
 */
public record EggTier(
        String key,
        List<String> nexoIds,
        int weight,
        String displayName
) {
    /**
     * Pick a random Nexo furniture ID from this tier's list.
     */
    public String randomNexoId() {
        return nexoIds.get(ThreadLocalRandom.current().nextInt(nexoIds.size()));
    }
}
