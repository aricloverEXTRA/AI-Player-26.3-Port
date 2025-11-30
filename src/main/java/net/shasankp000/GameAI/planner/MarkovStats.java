package net.shasankp000.GameAI.planner;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Statistics for a Markov transition.
 * Stores counts for each possible next action (0-39).
 */
public class MarkovStats implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final int NUM_ACTIONS = 40;

    private final int[] counts;
    private int total;

    public MarkovStats() {
        this.counts = new int[NUM_ACTIONS];
        this.total = 0;
    }

    /**
     * Record an observed transition to the given action.
     */
    public synchronized void observe(byte actionId) {
        if (actionId >= 0 && actionId < NUM_ACTIONS) {
            counts[actionId]++;
            total++;
        }
    }

    /**
     * Get the count for a specific action.
     */
    public int getCount(byte actionId) {
        if (actionId >= 0 && actionId < NUM_ACTIONS) {
            return counts[actionId];
        }
        return 0;
    }

    /**
     * Get total count of all transitions.
     */
    public int getTotal() {
        return total;
    }

    /**
     * Get probability of action with add-1 smoothing.
     */
    public double getProbability(byte actionId, double smoothingAlpha) {
        if (actionId < 0 || actionId >= NUM_ACTIONS) {
            return 0.0;
        }
        return (counts[actionId] + smoothingAlpha) / (total + smoothingAlpha * NUM_ACTIONS);
    }

    /**
     * Get all counts (for sampling).
     */
    public int[] getCounts() {
        return Arrays.copyOf(counts, counts.length);
    }

    @Override
    public String toString() {
        return String.format("MarkovStats{total=%d, nonZero=%d}",
                total, Arrays.stream(counts).filter(c -> c > 0).count());
    }
}

