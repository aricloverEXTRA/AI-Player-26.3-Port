package net.shasankp000.GameAI.handoff;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.shasankp000.GameAI.BotEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Routes bot item-pickup events to {@link ItemHandoffHandler}.
 *
 * <p>In Fabric API 0.155.2+26.2 {@code PlayerPickupItemCallback} no longer
 * exists.  Detection is done via {@link net.shasankp000.mixin.PlayerPickupMixin},
 * which injects into {@code PlayerEntity.pickUpItem(ItemEntity, int)} and
 * calls {@link #dispatch(Player, ItemEntity)} directly.
 *
 * <p>Call {@link #register()} once from {@code AIPlayer.onInitialize()} so
 * the registered flag is set and log messages appear as expected.
 */
public final class ItemHandoffListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("item-handoff-listener");

    private ItemHandoffListener() {}

    private static volatile boolean registered = false;

    /**
     * Marks the listener as active.  The actual hook is wired by
     * {@link net.shasankp000.mixin.PlayerPickupMixin} — this method just
     * records the intent so {@link #dispatch} knows to run.
     */
    public static void register() {
        if (registered) return;
        registered = true;
        LOGGER.info("[handoff-listener] ItemHandoffListener registered (mixin-backed).");
    }

    /**
     * Called by {@link net.shasankp000.mixin.PlayerPickupMixin} on every
     * server-side item pickup.  Filters for the active bot player and
     * delegates to {@link ItemHandoffHandler}.
     *
     * @param picker     the player who picked up the item
     * @param itemEntity the item that was picked up
     */
    public static void dispatch(Player picker, ItemEntity itemEntity) {
        if (!registered) return;
        if (!(picker instanceof ServerPlayer serverPlayer)) return;
        if (BotEventHandler.bot == null) return;
        if (!serverPlayer.getUUID().equals(BotEventHandler.bot.getUUID())) return;

        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty()) return;

        // Resolve the original thrower.
        // In 26.2, ItemEntity no longer exposes getThrower().
        // We use getOwner() as the primary signal (set when a player throws an item)
        // and fall back to checking the entity's NBT thrower UUID via the owner entity.
        ServerPlayer thrower = null;

        // Primary: getOwner() returns the entity that "owns" the item (set on throw)
        if (itemEntity.getOwner() instanceof ServerPlayer ownerPlayer
                && !ownerPlayer.getUUID().equals(serverPlayer.getUUID())) {
            thrower = ownerPlayer;
        }

        LOGGER.debug("[handoff-listener] bot '{}' picked up '{}' (thrower={})",
                serverPlayer.getName().getString(),
                stack.getHoverName().getString(),
                thrower != null ? thrower.getName().getString() : "none");

        ItemHandoffHandler.onBotPickedUpItem(serverPlayer, thrower, stack);
    }
}
