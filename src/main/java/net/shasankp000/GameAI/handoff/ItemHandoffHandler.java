package net.shasankp000.GameAI.handoff;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.shasankp000.GameAI.mood.MoodEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Handles the "Smart Item Handoff" feature (Feature 4.1–4.2).
 *
 * <p>When a player throws an item that the bot picks up this class:
 * <ol>
 *   <li>Classifies the item into an {@link ItemTier}.</li>
 *   <li>Applies a valence + arousal delta to the bot's {@link MoodEngine}.</li>
 *   <li>Sends a context-aware chat reaction from the bot back to the server.</li>
 * </ol>
 *
 * <h3>Mood impulse table</h3>
 * <pre>
 *  LEGENDARY  Δv=+0.40  Δa=+0.35   (bot becomes ELATED)
 *  EPIC       Δv=+0.25  Δa=+0.25   (bot becomes EXCITED/CONTENT)
 *  RARE       Δv=+0.12  Δa=+0.10   (gentle uplift)
 *  COMMON     Δv=+0.04  Δa=+0.02   (tiny positive tick)
 *  JUNK       Δv=-0.10  Δa=+0.05   (mild irritation / AGITATED lean)
 * </pre>
 */
public final class ItemHandoffHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("item-handoff");
    private static final Random RNG = new Random();

    private ItemHandoffHandler() { /* static API only */ }

    // -----------------------------------------------------------------------
    // Mood impulse deltas per tier
    // -----------------------------------------------------------------------

    private static final Map<ItemTier, float[]> IMPULSE = Map.of(
        ItemTier.LEGENDARY, new float[]{+0.40f, +0.35f},
        ItemTier.EPIC,      new float[]{+0.25f, +0.25f},
        ItemTier.RARE,      new float[]{+0.12f, +0.10f},
        ItemTier.COMMON,    new float[]{+0.04f, +0.02f},
        ItemTier.JUNK,      new float[]{-0.10f, +0.05f}
    );

    // -----------------------------------------------------------------------
    // Chat reaction pools  (persona-agnostic defaults; PromptBuilder may
    // override these in the LLM path once that is wired up)
    // -----------------------------------------------------------------------

    private static final Map<ItemTier, List<String>> REACTIONS = Map.of(
        ItemTier.LEGENDARY, List.of(
            "Oh wow — %s just gave me a %s! I... I can't believe it! 🤩",
            "Wait, is this actually a %s?! %s you absolute legend!! 🎉",
            "A %s?! From %s?! My day just got SO much better! ✨"
        ),
        ItemTier.EPIC, List.of(
            "%s gave me a %s — nice one! Really appreciate that 😄",
            "Oh, a %s! Thanks %s, this will come in very handy!",
            "Ooh, %s dropped a %s for me! That's pretty epic 🔥"
        ),
        ItemTier.RARE, List.of(
            "Thanks %s, I'll make good use of that %s!",
            "%s tossed me a %s — cheers! 👍",
            "A %s, nice. Appreciated, %s!"
        ),
        ItemTier.COMMON, List.of(
            "Got a %s from %s, cool.",
            "%s gave me a %s. I'll hold onto it.",
            "Thanks for the %s, %s."
        ),
        ItemTier.JUNK, List.of(
            "%s threw a %s at me... really? 😒",
            "Ugh, %s — a %s? You couldn't find anything better?",
            "A %s from %s. Gee, thanks for the... thoughtful gift. 🙄"
        )
    );

    // -----------------------------------------------------------------------
    // Public entry point
    // -----------------------------------------------------------------------

    /**
     * Called when the bot picks up an item that was thrown by a player.
     *
     * @param bot       the bot that picked up the item
     * @param thrower   the player who threw the item (may be {@code null} if
     *                  the item had no owner / was already on the ground)
     * @param stack     the item stack that was picked up
     */
    public static void onBotPickedUpItem(
            ServerPlayerEntity bot,
            ServerPlayerEntity thrower,
            ItemStack stack) {

        if (bot == null || stack == null || stack.isEmpty()) return;

        String botName     = bot.getName().getString();
        String itemName    = stack.getName().getString();
        String throwerName = (thrower != null) ? thrower.getName().getString() : "someone";

        ItemTier tier = ItemTier.of(stack);

        // 1. Apply mood impulse
        float[] delta = IMPULSE.getOrDefault(tier, new float[]{0f, 0f});
        MoodEngine.applyDelta(botName, delta[0], delta[1]);

        LOGGER.info("[handoff] {} picked up {} ({}) from {} — mood Δv={} Δa={}",
                botName, itemName, tier, throwerName, delta[0], delta[1]);

        // 2. Send chat reaction (only when thrown by a real player)
        if (thrower != null) {
            String reaction = pickReaction(tier, throwerName, itemName);
            // Bot sends the message as itself using the server command manager
            if (bot.getServer() != null) {
                // Use the server's thread to send chat safely
                bot.getServer().execute(() -> {
                    try {
                        bot.getServer().getCommandManager().executeWithPrefix(
                            bot.getCommandSource().withSilent().withMaxLevel(4),
                            "/say " + reaction
                        );
                    } catch (Exception e) {
                        LOGGER.warn("[handoff] Failed to send chat reaction: {}", e.getMessage());
                    }
                });
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static String pickReaction(ItemTier tier, String throwerName, String itemName) {
        List<String> pool = REACTIONS.getOrDefault(tier, REACTIONS.get(ItemTier.COMMON));
        String template = pool.get(RNG.nextInt(pool.size()));
        // Templates have two %s slots: (thrower, item) or (item, thrower) depending on phrasing.
        // We detect which order the template expects by looking at which name appears first.
        // For simplicity all templates use order: thrower first, item second.
        try {
            return String.format(template, throwerName, itemName);
        } catch (Exception e) {
            return throwerName + " gave me a " + itemName + "!";
        }
    }
}
