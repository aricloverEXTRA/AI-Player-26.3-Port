package net.shasankp000.GameAI.autonomous;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Watches the raw server chat stream for socially significant events and
 * injects conversational goal strings into the {@link AutonomousGoalEngine}.
 *
 * <p>This class is purely a <em>social</em> reactor — it never injects
 * survival or motor goals.  Those remain the responsibility of the RL system.
 *
 * <h3>Usage</h3>
 * Call {@link #process(String)} from {@code BotEventHandler} every time a
 * raw chat/system message arrives from the server.  If the message matches a
 * known pattern the listener injects an appropriate conversational goal via
 * {@link AutonomousGoalEngine#injectUrgentGoal(String)}.
 *
 * <h3>Pattern table</h3>
 * <pre>
 * Server message pattern                      → Injected goal
 * ─────────────────────────────────────────────────────────────────────────
 * &lt;player&gt; has made the advancement [X]      → congratulate &lt;player&gt; on getting [X] in chat
 * &lt;player&gt; was slain by / died               → express sympathy to &lt;player&gt; in chat
 * &lt;player&gt; joined the game                   → greet &lt;player&gt; in chat
 * &lt;player&gt; left the game                    → say goodbye to &lt;player&gt; in chat
 * &lt;player&gt; found [diamond/netherite/...item] → react to &lt;player&gt; finding [item] in chat
 * &lt;player&gt; entered the Nether               → wish &lt;player&gt; luck in the Nether in chat
 * &lt;player&gt; has reached the goal [X]         → cheer on &lt;player&gt; in chat
 * </pre>
 */
public class WorldEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("world-event-listener");

    // -------------------------------------------------------------------------
    // Event pattern descriptors
    // -------------------------------------------------------------------------

    private record EventPattern(Pattern pattern, GoalBuilder builder) {
        @FunctionalInterface
        interface GoalBuilder {
            String build(Matcher m);
        }
    }

    private static final List<EventPattern> PATTERNS = List.of(

        // Advancement
        new EventPattern(
            Pattern.compile("^(\\S+) has made the advancement \\[(.+?)\\]"),
            m -> "congratulate " + m.group(1) + " on getting the advancement '" + m.group(2) + "' in chat"
        ),

        // Goal (end-screen advancement)
        new EventPattern(
            Pattern.compile("^(\\S+) has reached the goal \\[(.+?)\\]"),
            m -> "cheer on " + m.group(1) + " for reaching the goal '" + m.group(2) + "' in chat"
        ),

        // Challenge advancement
        new EventPattern(
            Pattern.compile("^(\\S+) has completed the challenge \\[(.+?)\\]"),
            m -> "congratulate " + m.group(1) + " on completing the challenge '" + m.group(2) + "' in chat"
        ),

        // Death — "was slain by"
        new EventPattern(
            Pattern.compile("^(\\S+) was slain by (.+)"),
            m -> "express sympathy to " + m.group(1) + " for being slain by " + m.group(2) + " in chat"
        ),

        // Death — generic variants (drowned, fell, blew up, burned, etc.)
        new EventPattern(
            Pattern.compile("^(\\S+) (drowned|fell|blew up|burned|froze|starved|suffocated|was shot|was pummeled|was killed|went up in flames|tried to swim in lava|died)"),
            m -> "express sympathy to " + m.group(1) + " who " + m.group(2) + " in chat"
        ),

        // Player joined
        new EventPattern(
            Pattern.compile("^(\\S+) joined the game"),
            m -> "greet " + m.group(1) + " in chat"
        ),

        // Player left
        new EventPattern(
            Pattern.compile("^(\\S+) left the game"),
            m -> "say goodbye to " + m.group(1) + " in chat"
        ),

        // Entered the Nether
        new EventPattern(
            Pattern.compile("^(\\S+) entered the Nether"),
            m -> "wish " + m.group(1) + " luck in the Nether in chat"
        ),

        // Found notable item (diamond, netherite, etc.) — typical server plugin announcement
        new EventPattern(
            Pattern.compile("^(\\S+) (?:found|mined|discovered) (?:a |an |some )?(diamond|netherite|ancient debris|emerald)",
                            Pattern.CASE_INSENSITIVE),
            m -> "react excitedly to " + m.group(1) + " finding " + m.group(2) + " in chat"
        )
    );

    // -------------------------------------------------------------------------
    // Instance
    // -------------------------------------------------------------------------

    private final AutonomousGoalEngine engine;

    /** Name of the bot itself — used to avoid the bot reacting to its own messages. */
    private final String botName;

    public WorldEventListener(AutonomousGoalEngine engine, String botName) {
        this.engine  = engine;
        this.botName = botName;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Feed a raw server message into the listener.
     * Call this from {@code BotEventHandler} on every incoming chat/system message.
     *
     * @param rawMessage The full raw text of the server message.
     */
    public void process(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) return;

        // Strip leading brackets (e.g. "[Server] ", colour codes, etc.)
        String stripped = rawMessage
                .replaceAll("\u00a7.", "")           // strip Minecraft colour codes
                .replaceAll("^\\[Server]\\s*", "")    // strip [Server] prefix
                .trim();

        // Don't let the bot react to its own messages
        if (stripped.startsWith(botName + " ") || stripped.startsWith("[" + botName + "]")) {
            return;
        }

        for (EventPattern ep : PATTERNS) {
            Matcher m = ep.pattern().matcher(stripped);
            if (m.find()) {
                String goal = ep.builder().build(m);
                LOGGER.info("[world-event] '{}' → injecting goal: '{}'", stripped, goal);
                engine.injectUrgentGoal(goal);
                return; // one event → one goal; don't stack multiple reactions
            }
        }
    }
}
