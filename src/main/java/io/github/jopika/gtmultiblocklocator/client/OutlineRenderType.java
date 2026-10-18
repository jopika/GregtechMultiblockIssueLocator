package io.github.jopika.gtmultiblocklocator.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/** A dedicated color-only buffer: no depth testing or depth writes. */
final class OutlineRenderType extends RenderType {
    static final RenderType OUTLINE = create("gtmultiblocklocator_outline",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 4096, false, false,
            CompositeState.builder().setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL).createCompositeState(false));

    private OutlineRenderType() {
        super("unused", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
                4096, false, false, () -> {}, () -> {});
    }
}
