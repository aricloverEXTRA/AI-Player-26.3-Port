package net.shasankp000.GraphicalUserInterface;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.shasankp000.Overlay.ThreatDebugManager;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.UUID;

/**
 * Renders threat analysis debug information as colored bounding boxes
 * Similar to Minecraft's F3+B hitbox display
 */
public class ThreatDebugRenderer {

    private static final int TARGET_BOX_COLOR = 0x80FF0000; // Red with transparency
    private static final int EVALUATED_BOX_COLOR = 0x80FFAA00; // Orange with transparency

    /**
     * Render debug overlays for all tracked entities
     */
    public static void renderThreatOverlays(Camera camera) {
        if (!ThreatDebugManager.isDebugEnabled()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        Map<UUID, ThreatDebugManager.ThreatInfo> threats = ThreatDebugManager.getAllThreats();
        UUID currentTarget = ThreatDebugManager.getCurrentTarget();

        Vec3d cameraPos = camera.getPos();

        for (Map.Entry<UUID, ThreatDebugManager.ThreatInfo> entry : threats.entrySet()) {
            // Find entity by UUID in the world
            Entity entity = null;
            for (Entity e : client.world.getEntities()) {
                if (e.getUuid().equals(entry.getKey())) {
                    entity = e;
                    break;
                }
            }

            if (entity instanceof LivingEntity livingEntity && livingEntity.isAlive()) {
                boolean isTarget = entry.getKey().equals(currentTarget);

                // Render bounding box only
                // Red for target, orange for evaluated threats
                int boxColor = isTarget ? TARGET_BOX_COLOR : EVALUATED_BOX_COLOR;
                renderEntityBoundingBox(livingEntity, boxColor, cameraPos);
            }
        }
    }

    /**
     * Render a simple bounding box for an entity
     */
    private static void renderEntityBoundingBox(LivingEntity entity, int boxColor, Vec3d cameraPos) {
        // Calculate distance to camera
        double distance = entity.getPos().distanceTo(cameraPos);
        if (distance > 64.0) return; // Don't render if too far

        Box box = entity.getBoundingBox();

        // Setup matrices
        MatrixStack matrices = new MatrixStack();
        matrices.translate((float)(-cameraPos.x), (float)(-cameraPos.y), (float)(-cameraPos.z));

        VertexConsumerProvider.Immediate consumers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLines());

        float r = ((boxColor >> 16) & 0xFF) / 255.0f;
        float g = ((boxColor >> 8) & 0xFF) / 255.0f;
        float b = (boxColor & 0xFF) / 255.0f;
        float a = ((boxColor >> 24) & 0xFF) / 255.0f;

        // Draw bounding box edges
        drawBoxEdges(matrices.peek().getPositionMatrix(), buffer, box, r, g, b, a);
        consumers.draw(RenderLayer.getLines());

        // Restore state

    }

    /**
     * Draw the 12 edges of a bounding box (same as Minecraft hitbox display)
     */
    private static void drawBoxEdges(Matrix4f matrix, VertexConsumer buffer, Box box, float r, float g, float b, float a) {
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        // Bottom face edges
        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a).normal(1.0f, 0.0f, 0.0f);
        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a).normal(1.0f, 0.0f, 0.0f);

        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a).normal(0.0f, 0.0f, 1.0f);
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a).normal(0.0f, 0.0f, 1.0f);

        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a).normal(-1.0f, 0.0f, 0.0f);
        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a).normal(-1.0f, 0.0f, 0.0f);

        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a).normal(0.0f, 0.0f, -1.0f);
        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a).normal(0.0f, 0.0f, -1.0f);

        // Top face edges
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a).normal(1.0f, 0.0f, 0.0f);
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a).normal(1.0f, 0.0f, 0.0f);

        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a).normal(0.0f, 0.0f, 1.0f);
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a).normal(0.0f, 0.0f, 1.0f);

        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a).normal(-1.0f, 0.0f, 0.0f);
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a).normal(-1.0f, 0.0f, 0.0f);

        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a).normal(0.0f, 0.0f, -1.0f);
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a).normal(0.0f, 0.0f, -1.0f);

        // Vertical edges (connecting bottom to top)
        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a).normal(0.0f, 1.0f, 0.0f);
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a).normal(0.0f, 1.0f, 0.0f);

        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a).normal(0.0f, 1.0f, 0.0f);
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a).normal(0.0f, 1.0f, 0.0f);

        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a).normal(0.0f, 1.0f, 0.0f);
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a).normal(0.0f, 1.0f, 0.0f);

        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a).normal(0.0f, 1.0f, 0.0f);
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a).normal(0.0f, 1.0f, 0.0f);
    }

}