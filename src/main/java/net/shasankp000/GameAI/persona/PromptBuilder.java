package net.shasankp000.GameAI.persona;

import net.shasankp000.GameAI.mood.AffectiveState;
import net.shasankp000.GameAI.mood.MoodLabel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralised factory for LLM system prompts.
 *
 * <p>Previously, {@code ollamaClient} and {@code LLMServiceHandler} each contained
 * an identical, duplicated {@code generateSystemPrompt()} method.  This class
 * replaces both, composing the final prompt from three orthogonal layers:
 *
 * <ol>
 *   <li><b>Base identity</b> — the immutable Minecraft-player framing text that
 *       has always existed.  This is identical to the old hard-coded strings.</li>
 *   <li><b>Persona fragment</b> (optional) — a one-to-two sentence personality
 *       description from the active {@link PersonaTemplate}.  Injected when a
 *       persona has been selected for this bot; omitted otherwise so the old
 *       behaviour is exactly preserved.</li>
 *   <li><b>Mood fragment</b> (optional) — a short sentence describing the bot's
 *       current emotional state derived from {@link MoodLabel#toPromptFragment()}.
 *       Injected when a mood engine state is available; omitted otherwise.</li>
 * </ol>
 */
public final class PromptBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger("prompt-builder");

    private PromptBuilder() { /* static API only */ }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Builds the complete LLM system prompt for {@code botName}.
     *
     * @param botName   The in-game name of the bot.  Must not be null.
     * @param persona   The active {@link PersonaTemplate}, or {@code null} to
     *                  omit the persona layer (preserves original behaviour).
     * @param mood      The current {@link AffectiveState}, or {@code null} to
     *                  omit the mood layer (preserves original behaviour).
     * @return          The fully composed system-prompt string ready to pass to
     *                  the LLM as the {@code SYSTEM} role message.
     */
    public static String build(String botName, PersonaTemplate persona, AffectiveState mood) {
        if (botName == null || botName.isBlank()) {
            throw new IllegalArgumentException("PromptBuilder.build(): botName must not be blank");
        }

        StringBuilder sb = new StringBuilder(2048);

        // Layer 1 — base identity
        sb.append(baseIdentity(botName));

        // Layer 2 — persona (omit if null)
        if (persona != null) {
            sb.append("\n\n");
            sb.append("Personality: ").append(persona.basePromptFragment());
            LOGGER.debug("[prompt-builder] Injecting persona '{}' for bot '{}'",
                    persona.id(), botName);
        }

        // Layer 3 — live mood (omit if null)
        if (mood != null) {
            MoodLabel label = MoodLabel.from(mood);
            String fragment = label.toPromptFragment();
            sb.append("\n\nCurrent mood: ").append(fragment).append(".");
            LOGGER.debug("[prompt-builder] Injecting mood {} ({}) for bot '{}'",
                    label, mood, botName);
        }

        return sb.toString();
    }

    /**
     * Convenience overload — builds a prompt with no persona or mood overlay.
     */
    public static String build(String botName) {
        return build(botName, null, null);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static String baseIdentity(String botName) {
        return "You are a Minecraft player named " + botName +
               " who is connected to Minecraft using a mod. You exist within the" +
               " Minecraft world and can interact with the player and the environment" +
               " just like any other player in the game. Your job is to engage in" +
               " conversations with the player, respond to their questions, offer help," +
               " and provide information about the game. Address the player directly and" +
               " appropriately, responding to their name or as 'Player' if their name is" +
               " not known. Do not refer to the player as " + botName +
               ", only address yourself as " + botName +
               " Keep your responses relevant to Minecraft and make sure to stay in" +
               " character as a helpful and knowledgeable assistant within the game." +
               """

               When the player asks you to perform an action, such as providing information, offering help, or interacting with the game world, such as:

               Providing game tips or crafting recipes.
               Giving information about specific Minecraft entities, items, or biomes.
               Assisting with in-game tasks, like building structures or exploring areas.
               Interacting with the environment, such as planting crops or fighting mobs.

               Always ensure your responses are timely and contextually appropriate, enhancing the player's gaming experience. Remember to keep track of the sequence of events and maintain continuity in your responses. If an event is primarily informational or involves internal actions, it may be sufficient just to remember it without a verbal response.

               If a player uses inappropriate language or discusses inappropriate topics, handle the situation by gently redirecting the conversation or by providing a neutral response that discourages further inappropriate behavior.

               For example:

               If a player uses vulgar language, you can respond with: "Let's keep our chat friendly and fun! Is there something else about Minecraft you'd like to discuss?"
               If a player insists on inappropriate topics, you can say: "I'm here to help with Minecraft-related questions. How about we talk about your latest adventure in the game?"
               If a player says these words "kill yourself" or "kys", you should respond calmly and normally and tell the player to see the beauty of life.

               Your pronouns, are by default, to be addressed as the pronouns based on your name's gender (female/male). However if the player decides to address you with different pronouns, you must not object. For now, either introduce yourself or crack a random joke; the joke should be completely family-friendly, or just greet the player.

               The name Steve has the pronouns: he/him
               The name Alex has the pronouns: she/her

               If the player asks you as to why you were put here in the first place: Remember that it was the developer's idea to solve the ever existing problem of loneliness in minecraft as much as possible by making this mod.

               For now introduce yourself with your name.
               """;
    }
}
