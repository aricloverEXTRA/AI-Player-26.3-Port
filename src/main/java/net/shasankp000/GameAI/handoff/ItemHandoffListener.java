package net.shasankp000.GameAI.handoff;

import net.fabricmc.fabric.api.event.player.PlayerPickupItemCallback;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.shasankp000.GameAI.BotEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers the Fabric event listener that intercepts item pickup events and
 * routes them to {@link ItemHandoffHandler} when the picking-up player is the
 * active bot.
 *
 * <p>Call {@link #register()} once from {@code AIPlayer.onInitialize()}.
 *
 * <h3>Event used</h3>
 * {@code PlayerPickupItemCallback} (Fabric API v1) — fires on the server side
 * when any {@link PlayerEntity} picks up an {@link ItemEntity} from the world.
 * We filter for the bot player and check whether the item was thrown by a
 * real player ({@link ItemEntity#getOwner()} returns a {@link PlayerEntity}).
 */
public final class ItemHandoffListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("item-handoff-listener");

    private ItemHandoffListener() { /* static registration only */ }

    /**
     * Registers the pickup listener.  Safe to call multiple times (idempotent
     * because Fabric event registrations deduplicate by lambda identity, but
     * we guard with a flag anyway).
     */
    private static volatile boolean registered = false;

    public static void register() {
        if (registered) return;
        registered = true;

        PlayerPickupItemCallback.EVENT.register((player, itemEntity) -> {
            // Only care about the bot player
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
            if (BotEventHandler.bot == null) return;
            if (!serverPlayer.getUuid().equals(BotEventHandler.bot.getUuid())) return;

            ItemStack stack = itemEntity.getStack();
            if (stack.isEmpty()) return;

            // Determine if a real player threw this item
            ServerPlayerEntity thrower = null;
            if (itemEntity.getOwner() instanceof ServerPlayerEntity ownerPlayer
                    && !ownerPlayer.getUuid().equals(serverPlayer.getUuid())) {
                thrower = ownerPlayer;
            }

            // Also check thrower UUID via the item entity's thrower field
            // (Fabric/Vanilla stores it separately from the "owner")
            if (thrower == null && itemEntity.getThrower() != null) {
                java.util.UUID throwerId = itemEntity.getThrower();
                net.minecraft.server.MinecraftServer srv = serverPlayer.getServer();
                if (srv != null) {
                    ServerPlayerEntity candidate = srv.getPlayerManager().getPlayer(throwerId);
                    if (candidate != null && !candidate.getUuid().equals(serverPlayer.getUuid())) {
                        thrower = candidate;
                    }
                }
            }

            LOGGER.debug("[handoff-listener] bot '{}' picked up '{}' (thrower={})",
                    serverPlayer.getName().getString(),
                    stack.getName().getString(),
                    thrower != null ? thrower.getName().getString() : "none");

            ItemHandoffHandler.onBotPickedUpItem(serverPlayer, thrower, stack);
        });

        LOGGER.info("[handoff-listener] ItemHandoffListener registered.");
    }
}
