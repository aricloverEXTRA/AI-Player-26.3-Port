package net.shasankp000.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.shasankp000.GraphicalUserInterface.ThreatDebugRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to inject threat debug rendering into world rendering for 1.21.2+
 */
@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    @Inject(
        method = "render", 
        at = @At(
            value = "INVOKE", 
            target = "Lnet/minecraft/client/render/debug/DebugRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;DDD)V", 
            shift = At.Shift.AFTER
        )
    )
    private void onRenderWorld(
        RenderTickCounter tickCounter, 
        boolean renderBlockOutline, 
        Camera camera, 
        GameRenderer gameRenderer, 
        LightmapTextureManager lightmapTextureManager, 
        Matrix4f matrix4f, 
        Matrix4f matrix4f2, 
        CallbackInfo ci
    ) {
        // Render threat debug overlays in world space
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null && camera != null) {
            ThreatDebugRenderer.renderThreatOverlays(camera);
        }
    }
}
