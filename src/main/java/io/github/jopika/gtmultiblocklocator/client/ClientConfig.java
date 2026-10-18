package io.github.jopika.gtmultiblocklocator.client;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/** Local client preferences in config/gtmultiblocklocator-client.toml. */
public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue HIGHLIGHT_ENABLED;
    public static final ForgeConfigSpec.IntValue DURATION_SECONDS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("highlight");
        HIGHLIGHT_ENABLED = builder.comment("Enable clickable error coordinates and the world outline.")
                .define("enabled", true);
        DURATION_SECONDS = builder.comment("Outline duration in seconds, starting after the GUI closes.")
                .defineInRange("durationSeconds", 5, 1, 300);
        builder.pop();
        SPEC = builder.build();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SPEC);
    }

    private ClientConfig() {}
}
