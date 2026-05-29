package net.shasankp000.GameAI.autonomous;

import net.minecraft.server.network.ServerPlayerEntity;
import net.shasankp000.AIPlayer;
import net.shasankp000.GameAI.BotEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Drives two periodic jobs that keep the bot feeling alive:
 *
 * <ol>
 *   <li><b>Idle re-plan</b> (default every 5 min) — if the goal queue is empty
 *       or the bot has been idle for too long, asks the LLM for a fresh batch
 *       of goals via {@link AutonomousGoalEngine#triggerReplan()}.</li>
 *   <li><b>State drift check</b> (default every 30 s) — reads the bot's
 *       current game state and can insert a priority goal if something important
 *       has changed (e.g. inventory nearly full, bot is lost, etc.).</li>
 * </ol>
 *
 * <p>Both intervals are configurable via system properties:
 * <pre>
 *   -Daiplayer.autonomous.replanIntervalMin=5
 *   -Daiplayer.autonomous.driftCheckIntervalSec=30
 * </pre>
 */
public class AutonomousScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger("autonomous-scheduler");

    // Default intervals
    private static final int DEFAULT_REPLAN_MIN    = 5;
    private static final int DEFAULT_DRIFT_SEC     = 30;

    // How many consecutive drift checks of an idle queue before forcing a replan
    private static final int IDLE_REPLAN_THRESHOLD = 3;

    // -------------------------------------------------------------------------

    private final AutonomousGoalEngine engine;
    private final String botName;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> Thread.ofVirtual().name("autonomous-scheduler").unstarted(r));

    private ScheduledFuture<?> replanJob;
    private ScheduledFuture<?> driftJob;

    /** Consecutive drift-check cycles where the queue has been empty. */
    private int idleCycles = 0;

    // -------------------------------------------------------------------------

    public AutonomousScheduler(AutonomousGoalEngine engine, String botName) {
        this.engine  = engine;
        this.botName = botName;
    }

    /** Start both scheduled jobs. */
    public void start() {
        int replanMin = readIntProp("aiplayer.autonomous.replanIntervalMin", DEFAULT_REPLAN_MIN);
        int driftSec  = readIntProp("aiplayer.autonomous.driftCheckIntervalSec", DEFAULT_DRIFT_SEC);

        LOGGER.info("[scheduler] Starting — replan every {} min, drift check every {} sec",
                replanMin, driftSec);

        replanJob = scheduler.scheduleAtFixedRate(
                this::replanJob, replanMin, replanMin, TimeUnit.MINUTES);

        driftJob = scheduler.scheduleAtFixedRate(
                this::driftCheckJob, driftSec, driftSec, TimeUnit.SECONDS);
    }

    /** Cancel both jobs and shut down the executor. */
    public void shutdown() {
        if (replanJob != null) replanJob.cancel(false);
        if (driftJob  != null) driftJob.cancel(false);
        scheduler.shutdownNow();
        LOGGER.info("[scheduler] Shut down");
    }

    // -------------------------------------------------------------------------
    // Jobs
    // -------------------------------------------------------------------------

    private void replanJob() {
        if (engine.isPlayerControlled()) {
            LOGGER.debug("[scheduler] Replan skipped — player is controlling the bot");
            return;
        }
        LOGGER.info("[scheduler] Scheduled replan firing for bot '{}'", botName);
        engine.triggerReplan();
        idleCycles = 0;
    }

    private void driftCheckJob() {
        if (engine.isPlayerControlled()) return;

        // If queue has been empty for IDLE_REPLAN_THRESHOLD consecutive checks,
        // trigger an early replan rather than waiting for the full interval.
        if (engine.queueSize() == 0) {
            idleCycles++;
            LOGGER.debug("[scheduler] Queue empty — idle cycles: {}/{}",
                    idleCycles, IDLE_REPLAN_THRESHOLD);
            if (idleCycles >= IDLE_REPLAN_THRESHOLD) {
                LOGGER.info("[scheduler] Bot idle too long — triggering early replan");
                engine.triggerReplan();
                idleCycles = 0;
            }
            return;
        }
        idleCycles = 0;

        // State drift checks — inject priority goals for notable conditions
        try {
            ServerPlayerEntity bot = AIPlayer.serverInstance != null
                    ? AIPlayer.serverInstance.getPlayerManager().getPlayer(botName)
                    : null;

            if (bot == null) return;

            // Inventory nearly full → prompt the bot to organise/drop items
            int usedSlots = 0;
            for (int i = 0; i < bot.getInventory().size(); i++) {
                if (!bot.getInventory().getStack(i).isEmpty()) usedSlots++;
            }
            if (usedSlots >= 32) {
                LOGGER.info("[scheduler] Inventory nearly full ({}/36) — injecting organise goal", usedSlots);
                engine.injectUrgentGoal("organise inventory and drop excess items");
            }

            // Very low hunger — prompt eating (RL handles survival, but this
            // handles the slower 'go find food' planning layer)
            if (bot.getHungerManager().getFoodLevel() <= 4) {
                LOGGER.info("[scheduler] Hunger critical — injecting eat/gather food goal");
                engine.injectUrgentGoal("gather and eat food");
            }

        } catch (Exception e) {
            LOGGER.warn("[scheduler] Drift check error: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------

    private static int readIntProp(String key, int defaultValue) {
        try {
            String val = System.getProperty(key);
            return val != null ? Integer.parseInt(val) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
