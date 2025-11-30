package net.shasankp000.GameAI.planner;

import net.shasankp000.GameAI.RLAgent;
import net.shasankp000.GameAI.State;
import net.shasankp000.GameAI.planner.CheapForward.FakeState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Analyzes the risk/quality of an action sequence.
 * Combines: death risk, damage, time cost, Q-values, goal progress.
 */
public class SequenceRiskAnalyzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(SequenceRiskAnalyzer.class);

    // Tunable weights
    private static final double W_DEATH_RISK = 50.0;
    private static final double W_DAMAGE = 5.0;
    private static final double W_TIME_COST = 0.1;
    private static final double W_Q_BONUS = -10.0; // Negative = reward
    private static final double W_GOAL_PROGRESS = -20.0; // Negative = reward

    private final RLAgent rlAgent;
    private final net.minecraft.server.network.ServerPlayerEntity bot;

    public SequenceRiskAnalyzer(RLAgent rlAgent, net.minecraft.server.network.ServerPlayerEntity bot) {
        this.rlAgent = rlAgent;
        this.bot = bot;
    }

    /**
     * Score a sequence of actions (lower = better).
     *
     * @param steps The action sequence
     * @param initialState Starting game state
     * @param goalId Goal to achieve
     * @return Total score (lower = better, negative = good)
     */
    public double scoreSequence(List<PlannedStep> steps, State initialState, short goalId) {
        double totalDeathRisk = 0.0;
        double totalDamage = 0.0;
        double totalTimeCost;
        double totalQBonus = 0.0;

        // Initialize fake state for simulation
        FakeState fakeState = new FakeState(initialState);

        for (PlannedStep step : steps) {
            String actionName = step.getActionName();

            // Get risk estimates from RLAgent (uses comprehensive calculateRisk internally)
            RLAgent.RiskEstimate risk = rlAgent.estimateRisk(initialState, actionName, bot);
            totalDeathRisk += risk.deathRisk;
            totalDamage += risk.expectedDamage;

            // Get Q-value bonus
            double qValue = rlAgent.getQValue(initialState, actionName);
            totalQBonus += qValue;

            // Apply action to fake state
            CheapForward.applyAction(fakeState, step.getActionId());
        }

        // Time cost from fake state
        totalTimeCost = fakeState.timeCost;

        // Goal progress (negative = good)
        double goalProgress = CheapForward.computeGoalProgress(fakeState, goalId);

        // Compute final score
        double score = W_DEATH_RISK * totalDeathRisk
                     + W_DAMAGE * totalDamage
                     + W_TIME_COST * totalTimeCost
                     + W_Q_BONUS * totalQBonus
                     + W_GOAL_PROGRESS * goalProgress;

        LOGGER.debug("[risk-analyzer] Sequence score: {} (death={}, damage={}, time={}, q={}, progress={})",
                score, totalDeathRisk, totalDamage, totalTimeCost, totalQBonus, goalProgress);

        return score;
    }

    /**
     * Quick score estimate without detailed logging (for beam search).
     */
    public double quickScore(List<PlannedStep> steps, State initialState, short goalId) {
        FakeState fakeState = new FakeState(initialState);
        double risk = 0.0;

        for (PlannedStep step : steps) {
            CheapForward.applyAction(fakeState, step.getActionId());

            // Simplified risk estimate
            String actionName = step.getActionName();
            if (actionName.equals("attack") || actionName.equals("shoot_arrow")) {
                risk += 10.0;
            } else if (actionName.equals("evade")) {
                risk += 5.0;
            }
        }

        double goalProgress = CheapForward.computeGoalProgress(fakeState, goalId);
        return risk + W_TIME_COST * fakeState.timeCost + W_GOAL_PROGRESS * goalProgress;
    }
}

