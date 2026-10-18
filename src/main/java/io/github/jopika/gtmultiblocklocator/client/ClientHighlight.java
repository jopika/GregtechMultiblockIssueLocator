package io.github.jopika.gtmultiblocklocator.client;

import io.github.jopika.gtmultiblocklocator.GregTechMultiblockLocator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GregTechMultiblockLocator.ID, value = Dist.CLIENT)
public final class ClientHighlight {
    public record Target(ResourceKey<Level> dimension, BlockPos pos) {}
    private static final PendingCue<ClientLevel, Target> CUE = new PendingCue<>();

    public static void select(ClientLevel level, BlockPos pos) {
        if (ClientConfig.HIGHLIGHT_ENABLED.get()) {
            CUE.select(level, new Target(level.dimension(), pos.immutable()),
                    ClientConfig.DURATION_SECONDS.get() * 1_000_000_000L);
        }
    }

    public static Target visible() {
        if (!ClientConfig.HIGHLIGHT_ENABLED.get()) {
            CUE.clear();
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        return CUE.visible(mc.level, mc.screen != null, System.nanoTime());
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) visible();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        CUE.clear();
    }

    private ClientHighlight() {}
}
