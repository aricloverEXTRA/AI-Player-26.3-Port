package net.shasankp000.GameAI.planner;

import java.util.Objects;

/**
 * Compact key for Markov chain lookups.
 * Consists of: goalId, contextHash, prev2 action, prev1 action
 */
public class MarkovKey {
    private final short goalId;
    private final int contextHash;
    private final byte prev2;
    private final byte prev1;

    public MarkovKey(short goalId, int contextHash, byte prev2, byte prev1) {
        this.goalId = goalId;
        this.contextHash = contextHash;
        this.prev2 = prev2;
        this.prev1 = prev1;
    }

    public short getGoalId() {
        return goalId;
    }

    public int getContextHash() {
        return contextHash;
    }

    public byte getPrev2() {
        return prev2;
    }

    public byte getPrev1() {
        return prev1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MarkovKey that = (MarkovKey) o;
        return goalId == that.goalId &&
               contextHash == that.contextHash &&
               prev2 == that.prev2 &&
               prev1 == that.prev1;
    }

    @Override
    public int hashCode() {
        return Objects.hash(goalId, contextHash, prev2, prev1);
    }

    @Override
    public String toString() {
        return String.format("MarkovKey{goal=%d, ctx=%d, prev2=%d, prev1=%d}",
                goalId, contextHash, prev2, prev1);
    }
}

