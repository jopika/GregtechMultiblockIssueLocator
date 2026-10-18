package io.github.jopika.gtmultiblocklocator.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.github.jopika.gtmultiblocklocator.GregTechMultiblockLocator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

@Mod.EventBusSubscriber(modid = GregTechMultiblockLocator.ID, value = Dist.CLIENT)
public final class HighlightRenderer {
    private static final MultiBufferSource.BufferSource BUFFER =
            MultiBufferSource.immediate(new BufferBuilder(4096));
    private record Frame(Matrix4f view, Matrix4f projection, Vec3 camera, ClientLevel world, int tick) {}
    private static Frame frame;

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            // This stage has the actual view, but Fabulous can have a particle target bound.
            frame = ClientHighlight.visible() == null ? null : new Frame(
                    new Matrix4f(event.getPoseStack().last().pose()), new Matrix4f(event.getProjectionMatrix()),
                    event.getCamera().getPosition(), Minecraft.getInstance().level, event.getRenderTick());
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Frame current = frame;
        frame = null; // Consume once; never reuse a previous render's matrices.
        var target = ClientHighlight.visible();
        var level = Minecraft.getInstance().level;
        if (target == null || level == null || current == null || current.world() != level
                || current.tick() != event.getRenderTick() || !target.dimension().equals(level.dimension())) return;
        // AFTER_LEVEL is after world transparency composition. Its supplied PoseStack
        // is a projection stack in this Forge version; use the captured world view.
        var previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var previousSorting = RenderSystem.getVertexSorting();
        var previousColor = RenderSystem.getShaderColor().clone();
        var previousShader = RenderSystem.getShader();
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        int sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int destRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int destAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        try {
            modelView.setIdentity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(current.projection(), VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            // Vanilla NO_DEPTH_TEST is a no-op for GL_ALWAYS, not a disable call.
            RenderSystem.disableDepthTest();
            var camera = current.camera();
            // Subtract in double precision, before vertex conversion to float.
            double x = target.pos().getX() - camera.x;
            double y = target.pos().getY() - camera.y;
            double z = target.pos().getZ() - camera.z;
            var vertices = BUFFER.getBuffer(OutlineRenderType.OUTLINE);
            var view = current.view();
            double thickness = .012;
            // Twelve solid edge bars form a cube independently of block/voxel shape.
            for (int a = 0; a <= 1; a++) for (int b = 0; b <= 1; b++) {
                box(vertices, view, x - thickness, y + a - thickness, z + b - thickness,
                        x + 1 + thickness, y + a + thickness, z + b + thickness);
                box(vertices, view, x + a - thickness, y - thickness, z + b - thickness,
                        x + a + thickness, y + 1 + thickness, z + b + thickness);
                box(vertices, view, x + a - thickness, y + b - thickness, z - thickness,
                        x + a + thickness, y + b + thickness, z + 1 + thickness);
            }
            BUFFER.endBatch(OutlineRenderType.OUTLINE);
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(previousProjection, previousSorting);
            RenderSystem.setShaderColor(previousColor[0], previousColor[1], previousColor[2], previousColor[3]);
            RenderSystem.setShader(() -> previousShader);
            RenderSystem.depthMask(depthWrite);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            RenderSystem.blendFuncSeparate(sourceRgb, destRgb, sourceAlpha, destAlpha);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
        }
    }

    private static void box(VertexConsumer v, Matrix4f m, double x, double y, double z,
                            double X, double Y, double Z) {
        quad(v, m, x,y,z, X,y,z, X,Y,z, x,Y,z);
        quad(v, m, x,y,Z, x,Y,Z, X,Y,Z, X,y,Z);
        quad(v, m, x,y,z, x,Y,z, x,Y,Z, x,y,Z);
        quad(v, m, X,y,z, X,y,Z, X,Y,Z, X,Y,z);
        quad(v, m, x,y,z, x,y,Z, X,y,Z, X,y,z);
        quad(v, m, x,Y,z, X,Y,z, X,Y,Z, x,Y,Z);
    }

    private static void quad(VertexConsumer v, Matrix4f m,
                             double x1, double y1, double z1, double x2, double y2, double z2,
                             double x3, double y3, double z3, double x4, double y4, double z4) {
        vertex(v,m,x1,y1,z1); vertex(v,m,x2,y2,z2);
        vertex(v,m,x3,y3,z3); vertex(v,m,x4,y4,z4);
    }

    private static void vertex(VertexConsumer v, Matrix4f m, double x, double y, double z) {
        v.vertex(m, (float) x, (float) y, (float) z).color(1f, .25f, .15f, .9f).endVertex();
    }

    private HighlightRenderer() {}
}
