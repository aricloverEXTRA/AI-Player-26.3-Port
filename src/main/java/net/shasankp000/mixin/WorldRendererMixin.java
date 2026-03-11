package net.shasankp000.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.util.math.Vec3d;
import net.shasankp000.GraphicalUserInterface.ThreatDebugRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to inject threat debug rendering into world rendering
 */
@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    @Inject(method = "renderLateDebug(Lnet/minecraft/client/render/FrameGraphBuilder;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/client/render/Fog;)V", at = @At("TAIL"))
    private void onRenderLateDebug(FrameGraphBuilder frameGraphBuilder, Vec3d pos, Fog fog, CallbackInfo ci) {
        // Render threat debug overlays in world space
        MinecraftClient client = MinecraftClient.getInstance();
		Camera camera = client.gameRenderer.getCamera();
        if (client.world != null && camera != null) {
            ThreatDebugRenderer.renderThreatOverlays(camera);
        }
    }
}

