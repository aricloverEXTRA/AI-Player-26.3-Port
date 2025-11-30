package net.shasankp000.GameAI.planner;

import net.minecraft.server.network.ServerPlayerEntity;
import net.shasankp000.GameAI.State;
import net.shasankp000.GameAI.StateActions;
import net.shasankp000.GameAI.StateTransition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ActionLogWriter logs executed planning steps to StateTransition
 * and updates the MarkovChain2 model for learning.
 */
public class ActionLogWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger("ActionLogWriter");
    private static final ExecutorService ASYNC_EXECUTOR = Executors.newFixedThreadPool(2);

    private final MarkovChain2 markovChain;
    private final ServerPlayerEntity bot;

    public ActionLogWriter(MarkovChain2 markovChain, ServerPlayerEntity bot) {
        this.markovChain = markovChain;
        this.bot = bot;
    }

    /**
     * Log a single executed step asynchronously.
     *
     * @param planId The plan UUID
     * @param goalId The goal identifier
     * @param stepIndex Index of this step in the plan
     * @param action The action byte ID
     * @param params Action parameters (nullable)
     * @param stateBefore State before execution
     * @param stateAfter State after execution
     * @param riskBefore Pre-execution risk score
     * @param reward Reward received
     * @param died Whether bot died during execution
     * @param bot The bot player entity
     */
    public CompletableFuture<Void> logStepAsync(
            UUID planId,
            short goalId,
            int stepIndex,
            byte action,
            String params,
            State stateBefore,
            State stateAfter,
            double riskBefore,
            double reward,
            boolean died,
            ServerPlayerEntity bot) {

        return CompletableFuture.runAsync(() -> {
            try {
                // 1. Record transition in StateTransition
                StateActions.Action actionEnum = StateActions.Action.values()[action];
                StateTransition transition = new StateTransition(
                    stateBefore,
                    stateAfter,
                    actionEnum,
                    reward,
                    0.0, // podValue - calculated separately if needed
                    died,
                    died ? 0 : -1 // stepsUntilDeath
                );
                transitionHistory.addTransition(transition);

                // 2. Update Markov chain
                int contextHash = computeContextHash(stateBefore);
                byte prev2 = getPreviousAction(stepIndex, 2);
                byte prev1 = getPreviousAction(stepIndex, 1);

                markovChain.observeTransition(goalId, contextHash, prev2, prev1, action);

                // 3. Log for debugging
                LOGGER.info("✓ Logged step {} for plan {}: action={}, reward={}, died={}",
                    stepIndex, planId.toString().substring(0, 8), action, reward, died);

            } catch (Exception e) {
                LOGGER.error("Failed to log step {}: {}", stepIndex, e.getMessage(), e);
            }
        }, ASYNC_EXECUTOR);
    }

    /**
     * Simplified logStep method for use by FunctionCallerV2.
     * This is the version actually called during plan execution.
     */
    public void logStep(
            UUID planId,
            short goalId,
            State stateBefore,
            int stepIndex,
            PlannedStep step,
            String outcome,
            double reward,
            boolean died) {

        CompletableFuture.runAsync(() -> {
            try {
                // Update Markov chain
                int contextHash = computeContextHash(stateBefore);
                byte prev2 = 0; // Simplified - in real implementation track last 2 actions
                byte prev1 = 0;
                byte action = step.action;

                markovChain.observeTransition(goalId, contextHash, prev2, prev1, action);

                // Log for debugging
                LOGGER.info("✓ Logged step {} for plan {}: action={}, outcome={}, reward={}, died={}",
                    stepIndex, planId.toString().substring(0, 8), step.actionName, outcome, reward, died);

            } catch (Exception e) {
                LOGGER.error("Failed to log step {}: {}", stepIndex, e.getMessage(), e);
            }
        }, ASYNC_EXECUTOR);
    }

    /**
     * Log plan completion with final outcome.
     */
    public void logPlanComplete(UUID planId, short goalId, boolean success, double totalReward) {
        LOGGER.info("Plan {} completed: goalId={}, success={}, totalReward={}",
            planId.toString().substring(0, 8), goalId, success, totalReward);
    }

    /**
     * Compute a simple hash for state context (for Markov key).
     * Uses bucketed inventory signature + nearby entity types.
     */
    private int computeContextHash(State state) {
        int hash = 17;

        // Inventory signature (simplified)
        hash = 31 * hash + (state.getHotBarItems().contains("minecraft:diamond_sword") ? 1 : 0);
        hash = 31 * hash + (state.getHotBarItems().contains("minecraft:bow") ? 1 : 0);
        hash = 31 * hash + (state.getHotBarItems().contains("minecraft:shield") ? 1 : 0);

        // Nearby hostile count bucket
        long hostileCount = state.getNearbyEntities().stream()
            .filter(net.shasankp000.Entity.EntityDetails::isHostile)
            .count();
        hash = 31 * hash + (int)(hostileCount / 3); // bucket by 3s

        // Health bucket
        hash = 31 * hash + (state.getBotHealth() / 5);

        return hash;
    }

    /**
     * Get the previous action at offset (for Markov 2nd order).
     * Returns 0 if no previous action exists.
     */
    private byte getPreviousAction(int currentIndex, int offset) {
        if (currentIndex < offset) {
            return 0; // NO_ACTION / START
        }
        // In a real implementation, you'd track the last N actions in the Plan or in a local buffer
        // For now, return 0 as placeholder
        return 0;
    }

    /**
     * Shutdown the async executor gracefully.
     */
    public static void shutdown() {
        ASYNC_EXECUTOR.shutdown();
    }
}

