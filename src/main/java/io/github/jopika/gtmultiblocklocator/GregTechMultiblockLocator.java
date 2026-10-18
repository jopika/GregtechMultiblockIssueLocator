package io.github.jopika.gtmultiblocklocator;

import io.github.jopika.gtmultiblocklocator.client.ClientConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

/** Common entry point; all feature classes are loaded on the physical client only. */
@Mod(GregTechMultiblockLocator.ID)
public final class GregTechMultiblockLocator {
    public static final String ID = "gtmultiblocklocator";

    public GregTechMultiblockLocator() {
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ClientConfig::register);
        // Client-only network compatibility is declared by mods.toml's displayTest.
    }
}
