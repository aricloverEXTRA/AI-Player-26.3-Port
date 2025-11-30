package net.shasankp000.GameAI.planner;

import java.util.List;
import java.util.UUID;

/**
 * Represents a complete action plan with score.
 */
public class Plan {
    private final UUID planId;
    private final short goalId;
    private final List<PlannedStep> steps;
    private final double totalScore;

    public Plan(UUID planId, short goalId, List<PlannedStep> steps, double totalScore) {
        this.planId = planId;
        this.goalId = goalId;
        this.steps = steps;
        this.totalScore = totalScore;
    }

    public UUID getPlanId() {
        return planId;
    }

    public short getGoalId() {
        return goalId;
    }

    public List<PlannedStep> getSteps() {
        return steps;
    }

    public double getTotalScore() {
        return totalScore;
    }

    @Override
    public String toString() {
        return String.format("Plan{id=%s, goal=%d, steps=%d, score=%.2f}",
                planId, goalId, steps.size(), totalScore);
    }
}

