package net.shasankp000.GameAI.handoff;

import net.fabricmc.fabric.api.event.player.PlayerPickupItemCallback;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;
import net.shasankp000.GameAI.BotEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for item-throw events and drives the two-phase trade protocol.
 *
 * <h2>Phase 1 — Offer detection</h2>
 * When a player throws an item <em>while sneaking</em> that lands within
 * {@value #TRADE_RANGE} blocks of the bot AND no session already exists for
 * that player, a {@link TradeSession} is opened and the bot announces its
 * counter-offer in chat.
 *
 * <h2>Phase 2 — Delivery confirmation</h2>
 * When the same player throws a second item (matching the original offer item)
 * the bot completes the swap: it removes the counter-offer item from its own
 * inventory and drops it at its feet for the player to collect.
 *
 * <p>Sessions that expire ({@link TradeSession#EXPIRY_TICKS} ticks) are pruned
 * on every hook invocation.
 */
public final class TradeListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("ai-player");

    /** Radius in blocks within which a thrown item triggers a trade. */
    private static final double TRADE_RANGE = 6.0;

    /** Active sessions, keyed by thrower UUID. */
    private static final Map<UUID, TradeSession> SESSIONS = new ConcurrentHashMap<>();

    private TradeListener() {}

    // ── Registration ────────────────────────────────────────────────────────

    /**
     * Call once during mod initialisation (e.g. from your main
     * {@code ModInitializer}) to wire up the pickup callback.
     */
    public static void register() {
        PlayerPickupItemCallback.EVENT.register(TradeListener::onPlayerPickupItem);
        LOGGER.info("[trade] TradeListener registered");
    }

    // ── Tick-based expiry (call from ServerTickEvents or BotEventHandler) ───

    /**
     * Prunes expired sessions.  Hook this into a server tick event or call it
     * from any per-tick path inside {@link BotEventHandler}.
     */
    public static void tickPrune(long currentTick) {
        SESSIONS.entrySet().removeIf(e -> e.getValue().isExpired(currentTick));
    }

    // ── PlayerPickupItemCallback ─────────────────────────────────────────────

    /**
     * Intercepts every item pick-up event on the server side.
     * We use the pick-up callback (not throw) because Fabric 1.21.1 does not
     * expose a clean server-side "item thrown" event; instead we check
     * {@link ItemEntity#getThrower()} when the bot or any entity is about to
     * pick up a dropped item.
     *
     * <p>If the item was thrown by a sneaking human player near the bot,
     * we intercept it here and handle it ourselves, returning
     * {@link ActionResult#FAIL} to prevent normal pickup.
     */
    private static ActionResult onPlayerPickupItem(PlayerEntity picker,
                                                   ItemEntity itemEntity) {
        // We only care about items thrown by a human player.
        UUID throwerUuid = itemEntity.getThrower();
        if (throwerUuid == null) return ActionResult.PASS;

        ServerPlayerEntity bot = BotEventHandler.bot;
        if (bot == null) return ActionResult.PASS;

        // Resolve the thrower to a ServerPlayerEntity.
        MinecraftServer server = bot.getServer();
        if (server == null) return ActionResult.PASS;

        ServerPlayerEntity thrower = server.getPlayerManager().getPlayer(throwerUuid);
        if (thrower == null) return ActionResult.PASS;
        if (thrower.getUuid().equals(bot.getUuid())) return ActionResult.PASS; // bot threw it

        // Only react when the item lands near the bot.
        double distToBot = itemEntity.getPos().distanceTo(bot.getPos());
        if (distToBot > TRADE_RANGE) return ActionResult.PASS;

        // Only react when the original thrower was sneaking at throw-time.
        // We approximate this by checking whether the player is still sneaking;
        // for reliability you may want to store the sneak flag on throw instead.
        if (!thrower.isSneaking()) return ActionResult.PASS;

        ItemStack thrown = itemEntity.getStack();
        if (thrown.isEmpty()) return ActionResult.PASS;

        long currentTick = bot.getServerWorld().getTime();
        tickPrune(currentTick);

        TradeSession existing = SESSIONS.get(throwerUuid);

        if (existing == null) {
            // ── Phase 1: open a new session ──────────────────────────────────
            ItemStack counterOffer = TradeEvaluator.evaluate(thrown, bot);

            if (counterOffer.isEmpty()) {
                thrower.sendMessage(
                    Text.literal("§e[" + bot.getName().getString() + "] §fSorry, I have nothing fair to offer for that."),
                    false);
                return ActionResult.PASS; // let the item sit; player can re-pick it up
            }

            TradeSession session = new TradeSession(throwerUuid, thrown, counterOffer, currentTick);
            SESSIONS.put(throwerUuid, session);

            // Bot announces the deal in chat.
            String botName  = bot.getName().getString();
            String offName  = TradeEvaluator.displayName(thrown);
            String ctrName  = TradeEvaluator.displayName(counterOffer);
            thrower.sendMessage(
                Text.literal("§e[" + botName + "] §fI'll trade my §b" + ctrName
                    + "§f for your §b" + offName
                    + "§f. Throw the §b" + offName + "§f again to confirm!"),
                false);

            LOGGER.info("[trade] Session opened: {} offers {} for {}",
                thrower.getName().getString(), offName, ctrName);

            // Prevent the item from being picked up by anyone — it stays on the ground.
            return ActionResult.FAIL;

        } else {
            // ── Phase 2: complete the trade ──────────────────────────────────
            // Confirm the player is delivering the promised item.
            if (thrown.getItem() != existing.offeredItem.getItem()) {
                thrower.sendMessage(
                    Text.literal("§e[" + bot.getName().getString() + "] §fThat's not what we agreed on."),
                    false);
                return ActionResult.PASS;
            }

            // Remove the counter-offer item from the bot's inventory.
            boolean removed = removeOneFromBotInventory(bot, existing.counterOfferItem);
            if (!removed) {
                thrower.sendMessage(
                    Text.literal("§e[" + bot.getName().getString()
                        + "] §fI can't find that item in my inventory anymore. Trade cancelled."),
                    false);
                SESSIONS.remove(throwerUuid);
                return ActionResult.PASS;
            }

            // Give the bot the offered item (add to its inventory).
            bot.getInventory().insertStack(thrown.copy());
            itemEntity.discard(); // consume the dropped item entity

            // Drop the counter-offer at the bot's feet so the player can pick it up.
            dropItemNearBot(bot, existing.counterOfferItem.copy());

            String botName = bot.getName().getString();
            thrower.sendMessage(
                Text.literal("§e[" + botName + "] §fDeal! Enjoy your §b"
                    + TradeEvaluator.displayName(existing.counterOfferItem) + "§f!"),
                false);

            LOGGER.info("[trade] Trade completed: {} gave {}, received {}",
                thrower.getName().getString(),
                TradeEvaluator.displayName(existing.offeredItem),
                TradeEvaluator.displayName(existing.counterOfferItem));

            SESSIONS.remove(throwerUuid);
            return ActionResult.FAIL; // item was consumed; block further pickup handling
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Removes exactly one item matching {@code template} from the bot's
     * inventory.  Returns true on success.
     */
    private static boolean removeOneFromBotInventory(ServerPlayerEntity bot, ItemStack template) {
        for (int slot = 0; slot < bot.getInventory().size(); slot++) {
            ItemStack stack = bot.getInventory().getStack(slot);
            if (!stack.isEmpty() && stack.getItem() == template.getItem()) {
                stack.decrement(1);
                if (stack.isEmpty()) {
                    bot.getInventory().setStack(slot, ItemStack.EMPTY);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Drops an item at the bot's current position (offset slightly upward so
     * it doesn't clip into the ground).
     */
    private static void dropItemNearBot(ServerPlayerEntity bot, ItemStack stack) {
        ServerWorld world = bot.getServerWorld();
        Vec3d pos = bot.getPos().add(0, 0.5, 0);
        ItemEntity ie = new ItemEntity(world, pos.x, pos.y, pos.z, stack);
        ie.setVelocity(0, 0.1, 0); // gentle upward toss
        world.spawnEntity(ie);
    }

    // ── Package-level session access (for /bot trade command) ────────────────

    /** Returns the active session for {@code playerUuid}, or null. */
    static TradeSession getSession(UUID playerUuid) {
        return SESSIONS.get(playerUuid);
    }

    /** Forcibly removes a session (used by /bot trade cancel). */
    static void cancelSession(UUID playerUuid) {
        SESSIONS.remove(playerUuid);
    }
}
