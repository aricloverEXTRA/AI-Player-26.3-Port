package net.shasankp000.GameAI.planner;

/**
 * Represents a single planned action step.
 */
public class PlannedStep {
    private final byte actionId;
    private final String actionName;
    private double estimatedRisk;
    private final String params;

    public PlannedStep(byte actionId, String actionName, double estimatedRisk, String params) {
        this.actionId = actionId;
        this.actionName = actionName;
        this.estimatedRisk = estimatedRisk;
        this.params = params;
    }

    public byte getActionId() {
        return actionId;
    }

    public String getActionName() {
        return actionName;
    }

    public double getEstimatedRisk() {
        return estimatedRisk;
    }

    public void setEstimatedRisk(double risk) {
        this.estimatedRisk = risk;
    }

    public String getParams() {
        return params;
    }

    @Override
    public String toString() {
        return String.format("%s (id=%d, risk=%.2f, params=%s)",
                           actionName, actionId, estimatedRisk, params);
    }
}

