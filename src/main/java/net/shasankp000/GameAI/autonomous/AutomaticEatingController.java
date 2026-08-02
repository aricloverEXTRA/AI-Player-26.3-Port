package net.shasankp000.GameAI.autonomous;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.shasankp000.Entity.AutoFaceEntity;
import net.shasankp000.GameAI.BotEventHandler;
import net.shasankp000.PathFinding.PathTracer;
import net.shasankp000.PlayerUtils.blockDetectionUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Performs idle-time, vanilla-duration eating for the active AI bot.
 *
 * <p>The controller deliberately yields to combat, navigation, autonomous goals,
 * and other item use. Food from the main inventory is temporarily swapped with
 * the selected hotbar slot and restored after eating finishes or is interrupted.
 */
public final class AutomaticEatingController {
    private static final Logger LOGGER = LoggerFactory.getLogger("automatic-eating");

    public static final int HUNGER_THRESHOLD = 8;

    private static final int INVENTORY_SEARCH_SIZE = 36;
    private static final int RETRY_DELAY_TICKS = 10;
    private static final int MAX_EATING_TICKS = 100;

    private static final Set<String> UNSAFE_FOODS = Set.of(
            "minecraft:chorus_fruit",
            "minecraft:poisonous_potato",
            "minecraft:pufferfish",
            "minecraft:chicken",
            "minecraft:rotten_flesh",
            "minecraft:spider_eye",
            "minecraft:suspicious_stew"
    );

    private static final Set<String> RARE_UTILITY_FOODS = Set.of(
            "minecraft:enchanted_golden_apple",
            "minecraft:golden_apple"
    );

    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);
    private static final Map<UUID, EatingSession> SESSIONS = new HashMap<>();
    private static final Map<UUID, Integer> NEXT_ATTEMPT_TICK = new HashMap<>();
    private static final Set<UUID> WAITING_TO_EAT = ConcurrentHashMap.newKeySet();

    private AutomaticEatingController() {}

    /** Registers the server-tick callback once for the lifetime of the mod. */
    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) return;
        ServerTickEvents.END_SERVER_TICK.register(AutomaticEatingController::tick);
        LOGGER.info("Automatic eating registered (hunger threshold: {})", HUNGER_THRESHOLD);
    }

    /** Cancels and forgets automatic eating state for one bot. */
    public static void clear(ServerPlayer bot) {
        if (bot == null) return;
        EatingSession session = SESSIONS.remove(bot.getUUID());
        if (session != null) {
            stopAndRestore(session, "bot lifecycle ended");
        }
        NEXT_ATTEMPT_TICK.remove(bot.getUUID());
        WAITING_TO_EAT.remove(bot.getUUID());
    }

    /** Cancels all sessions. Called during server shutdown. */
    public static void reset() {
        for (EatingSession session : SESSIONS.values()) {
            stopAndRestore(session, "server stopping");
        }
        SESSIONS.clear();
        NEXT_ATTEMPT_TICK.clear();
        WAITING_TO_EAT.clear();
    }

    /**
     * Returns whether an autonomous worker should wait between goals so the
     * server-tick controller gets an idle window in which to eat.
     */
    public static boolean shouldPauseGoals(UUID botId) {
        return WAITING_TO_EAT.contains(botId);
    }

    private static void tick(MinecraftServer server) {
        ServerPlayer bot = BotEventHandler.bot;
        if (bot == null) {
            reset();
            return;
        }

        UUID botId = bot.getUUID();
        EatingSession session = SESSIONS.get(botId);

        if (!isAvailable(bot)) {
            clear(bot);
            return;
        }

        if (session != null) {
            tickSession(server, session);
            return;
        }

        if (bot.getFoodData().getFoodLevel() > HUNGER_THRESHOLD) {
            WAITING_TO_EAT.remove(botId);
            return;
        }

        FoodCandidate candidate = findBestFood(bot);
        if (candidate == null) {
            WAITING_TO_EAT.remove(botId);
            NEXT_ATTEMPT_TICK.put(botId, server.getTickCount() + RETRY_DELAY_TICKS);
            return;
        }

        WAITING_TO_EAT.add(botId);
        if (isBusy(bot, true)) return;
        if (server.getTickCount() < NEXT_ATTEMPT_TICK.getOrDefault(botId, 0)) return;

        startEating(server, bot, candidate);
    }

    private static void tickSession(MinecraftServer server, EatingSession session) {
        ServerPlayer bot = session.bot;

        if (isBusy(bot, false)) {
            SESSIONS.remove(bot.getUUID());
            stopAndRestore(session, "higher-priority activity started");
            scheduleRetry(server, bot);
            return;
        }

        if (bot.isUsingItem()) {
            if (server.getTickCount() - session.startedAtTick > MAX_EATING_TICKS) {
                SESSIONS.remove(bot.getUUID());
                stopAndRestore(session, "eating timed out");
                scheduleRetry(server, bot);
            }
            return;
        }

        SESSIONS.remove(bot.getUUID());
        restoreInventory(session);
        if (bot.getFoodData().getFoodLevel() > HUNGER_THRESHOLD) {
            WAITING_TO_EAT.remove(bot.getUUID());
        }
        scheduleRetry(server, bot);
        LOGGER.info("{} finished eating; hunger is now {}/20",
                bot.getName().getString(), bot.getFoodData().getFoodLevel());
    }

    private static void startEating(MinecraftServer server, ServerPlayer bot, FoodCandidate candidate) {
        Inventory inventory = bot.getInventory();
        int previousSelectedSlot = inventory.getSelectedSlot();
        int useSlot = candidate.slot < 9 ? candidate.slot : previousSelectedSlot;
        boolean swapped = candidate.slot != useSlot;
        ItemStack displacedStack = swapped ? inventory.getItem(useSlot) : ItemStack.EMPTY;

        if (swapped) swapSlots(inventory, candidate.slot, useSlot);
        inventory.setSelectedSlot(useSlot);
        syncInventory(bot);

        EatingSession session = new EatingSession(
                bot,
                previousSelectedSlot,
                candidate.slot,
                useSlot,
                swapped,
                displacedStack,
                server.getTickCount()
        );

        ItemStack handStack = bot.getItemInHand(InteractionHand.MAIN_HAND);
        InteractionResult result = bot.gameMode.useItem(
                bot, bot.level(), handStack, InteractionHand.MAIN_HAND);

        if (!result.consumesAction() || !bot.isUsingItem()) {
            restoreInventory(session);
            scheduleRetry(server, bot);
            LOGGER.debug("Could not start eating {} for {}", candidate.itemId, bot.getName().getString());
            return;
        }

        SESSIONS.put(bot.getUUID(), session);
        LOGGER.info("{} started eating {} from inventory slot {} at hunger {}/20",
                bot.getName().getString(), candidate.itemId, candidate.slot,
                bot.getFoodData().getFoodLevel());
    }

    private static FoodCandidate findBestFood(ServerPlayer bot) {
        FoodCandidate bestOrdinary = null;
        FoodCandidate bestUtility = null;
        int slots = Math.min(INVENTORY_SEARCH_SIZE, bot.getInventory().getContainerSize());

        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = bot.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;

            FoodProperties food = stack.get(DataComponents.FOOD);
            Consumable consumable = stack.get(DataComponents.CONSUMABLE);
            if (food == null || consumable == null || !consumable.canConsume(bot, stack)) continue;

            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (UNSAFE_FOODS.contains(itemId)) continue;

            FoodCandidate candidate = new FoodCandidate(
                    slot,
                    itemId,
                    food.nutrition(),
                    food.saturation()
            );

            if (RARE_UTILITY_FOODS.contains(itemId)) {
                if (bestUtility == null || FoodCandidate.BEST_FIRST.compare(candidate, bestUtility) < 0) {
                    bestUtility = candidate;
                }
            } else if (bestOrdinary == null || FoodCandidate.BEST_FIRST.compare(candidate, bestOrdinary) < 0) {
                bestOrdinary = candidate;
            }
        }

        return bestOrdinary != null ? bestOrdinary : bestUtility;
    }

    private static boolean isAvailable(ServerPlayer bot) {
        return bot.isAlive()
                && !bot.isRemoved()
                && !bot.hasDisconnected()
                && bot.gameMode.isSurvival()
                && !bot.isSleeping();
    }

    /**
     * @param includeItemUse true before starting; false while tracking this
     *                       controller's own eating animation.
     */
    private static boolean isBusy(ServerPlayer bot, boolean includeItemUse) {
        if (includeItemUse && bot.isUsingItem()) return true;
        if (AutoFaceEntity.botBusy
                || AutoFaceEntity.isBotExecutingTask()
                || AutoFaceEntity.isBotMoving
                || AutoFaceEntity.isShooting
                || AutoFaceEntity.isActivelyBlocking
                || AutoFaceEntity.isDefendingFromProjectile()) {
            return true;
        }
        if (AutoFaceEntity.hostileEntities != null && !AutoFaceEntity.hostileEntities.isEmpty()) return true;
        if (PathTracer.BotSegmentManager.getBotMovementStatus()
                || blockDetectionUnit.getBlockDetectionStatus()) {
            return true;
        }

        AutonomousGoalEngine engine = AutonomousManager.getInstance()
                .getEngine(bot.getName().getString());
        return engine != null && (engine.isExecutingGoal() || engine.isPlayerControlled());
    }

    private static void stopAndRestore(EatingSession session, String reason) {
        if (session.bot.isUsingItem()) session.bot.stopUsingItem();
        restoreInventory(session);
        LOGGER.debug("Stopped automatic eating for {}: {}", session.bot.getName().getString(), reason);
    }

    private static void restoreInventory(EatingSession session) {
        Inventory inventory = session.bot.getInventory();

        if (session.swapped) {
            // Only undo our swap when the displaced stack is still where we put it.
            // If another subsystem changed the slot, avoiding an overwrite is safer
            // than attempting to reconstruct inventory state from stale snapshots.
            if (inventory.getItem(session.foodSlot) == session.displacedStack) {
                swapSlots(inventory, session.foodSlot, session.useSlot);
            } else {
                LOGGER.warn("Skipped automatic-eating inventory restore for {} because slot {} changed",
                        session.bot.getName().getString(), session.foodSlot);
            }
        }

        inventory.setSelectedSlot(session.previousSelectedSlot);
        syncInventory(session.bot);
    }

    private static void scheduleRetry(MinecraftServer server, ServerPlayer bot) {
        NEXT_ATTEMPT_TICK.put(bot.getUUID(), server.getTickCount() + RETRY_DELAY_TICKS);
    }

    private static void swapSlots(Inventory inventory, int first, int second) {
        ItemStack firstStack = inventory.getItem(first);
        ItemStack secondStack = inventory.getItem(second);
        inventory.setItem(first, secondStack);
        inventory.setItem(second, firstStack);
    }

    private static void syncInventory(ServerPlayer bot) {
        bot.getInventory().setChanged();
        bot.containerMenu.broadcastChanges();
    }

    private record EatingSession(
            ServerPlayer bot,
            int previousSelectedSlot,
            int foodSlot,
            int useSlot,
            boolean swapped,
            ItemStack displacedStack,
            int startedAtTick
    ) {}

    private record FoodCandidate(int slot, String itemId, int nutrition, float saturation) {
        private static final Comparator<FoodCandidate> BEST_FIRST =
                Comparator.comparingDouble(FoodCandidate::score).reversed()
                        .thenComparing(Comparator.comparingInt(FoodCandidate::nutrition).reversed())
                        .thenComparingInt(FoodCandidate::slot);

        private double score() {
            return nutrition + saturation;
        }
    }
}
