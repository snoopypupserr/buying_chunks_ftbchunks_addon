package snoopypupser.buyingchunks.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class BuyingChunksConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue PARTICLE_RADIUS;
    public static final ModConfigSpec.IntValue PARTICLE_STEP;
    public static final ModConfigSpec.IntValue PARTICLE_INTERVAL;

    static {
        BUILDER.push("Particle Overlay");
        PARTICLE_RADIUS = BUILDER
                .comment("Reichweite fur Partikel-Overlay (in Chunks)")
                .defineInRange("particleRadius", 3, 1, 8);
        PARTICLE_STEP = BUILDER
                .comment("Abstand zwischen Partikeln entlang der Chunk-Kante (1 = dicht, 4 = sparlicher)")
                .defineInRange("particleStep", 4, 1, 8);
        PARTICLE_INTERVAL = BUILDER
                .comment("Frames zwischen Partikel-Spawns (1 = jedes Frame, 6 = alle 6 Frames)")
                .defineInRange("particleInterval", 6, 1, 20);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
