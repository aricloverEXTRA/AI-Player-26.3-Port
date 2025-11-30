package net.shasankp000.GameAI.planner;

import net.shasankp000.GameAI.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 2nd-order Markov chain for action sequence prediction.
 * Uses goal-conditioned transitions with context hashing.
 */
public class MarkovChain2 {
    private static final Logger LOGGER = LoggerFactory.getLogger("planner");
    private static final String SAVE_DIR = "markov_data";
    private static final double SMOOTHING_ALPHA = 1.0; // Add-1 smoothing

    // Key = (goalId, contextHash, prev2, prev1) -> Value = action counts
    private final ConcurrentHashMap<MarkovKey, MarkovStats> transitions;

    // Action registry (actionId -> name mapping)
    private final Map<Byte, String> actionRegistry;

    // Random for sampling
    private final Random random;

    public MarkovChain2() {
        this.transitions = new ConcurrentHashMap<>(10000);
        this.actionRegistry = new HashMap<>();
        this.random = new Random();

        // Initialize action registry
        initializeActionRegistry();

        // Try to load existing data
        loadFromDisk();
    }

    /**
     * Initialize action ID mappings (matches PlannedStep actions).
     */
    private void initializeActionRegistry() {
        // Core actions
        actionRegistry.put((byte) 0, "idle");
        actionRegistry.put((byte) 1, "move_forward");
        actionRegistry.put((byte) 2, "move_backward");
        actionRegistry.put((byte) 3, "turn_left");
        actionRegistry.put((byte) 4, "turn_right");
        actionRegistry.put((byte) 5, "jump");
        actionRegistry.put((byte) 6, "sneak");
        actionRegistry.put((byte) 7, "sprint");

        // Combat actions
        actionRegistry.put((byte) 10, "attack");
        actionRegistry.put((byte) 11, "shoot_arrow");
        actionRegistry.put((byte) 12, "use_shield");
        actionRegistry.put((byte) 13, "evade");

        // Utility actions
        actionRegistry.put((byte) 20, "mine_block");
        actionRegistry.put((byte) 21, "place_block");
        actionRegistry.put((byte) 22, "eat_food");
        actionRegistry.put((byte) 23, "equip_armor");
        actionRegistry.put((byte) 24, "craft_item");
        actionRegistry.put((byte) 25, "use_torch");

        // Hotbar actions
        for (int i = 1; i <= 9; i++) {
            actionRegistry.put((byte) (30 + i), "hotbar_" + i);
        }
    }

    /**
     * Draft a plan using Markov sampling.
     *
     * @param goalId Goal identifier
     * @param context Current game state
     * @param maxLen Maximum sequence length
     * @param epsilon Exploration rate (0-1)
     * @return List of planned steps
     */
    public List<PlannedStep> draftPlan(short goalId, State context, int maxLen, double epsilon) {
        List<PlannedStep> plan = new ArrayList<>();
        int contextHash = computeContextHash(context);

        byte prev2 = 0; // Initial "null" actions
        byte prev1 = 0;

        for (int i = 0; i < maxLen; i++) {
            byte nextAction;

            // Epsilon-greedy: explore vs exploit
            if (random.nextDouble() < epsilon) {
                // Random exploration
                nextAction = (byte) random.nextInt(40); // 0-39 action range
            } else {
                // Sample from Markov chain
                nextAction = sampleNextAction(goalId, contextHash, prev2, prev1);
            }

            // Check if action is pointless in current context
            if (isPointlessAction(context, nextAction)) {
                continue; // Skip and try next
            }

            // Add to plan
            String actionName = actionRegistry.getOrDefault(nextAction, "unknown_" + nextAction);
            plan.add(new PlannedStep(nextAction, actionName, 0.0, null));

            // Update history
            prev2 = prev1;
            prev1 = nextAction;

            // Early stopping if we hit a terminal action
            if (isTerminalAction(nextAction)) {
                break;
            }
        }

        return plan;
    }

    /**
     * Sample next action from Markov distribution.
     */
    private byte sampleNextAction(short goalId, int contextHash, byte prev2, byte prev1) {
        MarkovKey key = new MarkovKey(goalId, contextHash, prev2, prev1);
        MarkovStats stats = transitions.get(key);

        if (stats == null || stats.total == 0) {
            // No data, return random action
            return (byte) random.nextInt(40);
        }

        // Compute smoothed probabilities
        int vocabSize = 40; // Number of possible actions
        double[] probs = new double[vocabSize];
        double sumProbs = 0.0;

        for (int i = 0; i < vocabSize; i++) {
            // Add-1 smoothing: P(action) = (count + alpha) / (total + alpha * vocab_size)
            probs[i] = (stats.counts[i] + SMOOTHING_ALPHA) / (stats.total + SMOOTHING_ALPHA * vocabSize);
            sumProbs += probs[i];
        }

        // Sample from distribution
        double rand = random.nextDouble() * sumProbs;
        double cumulative = 0.0;

        for (int i = 0; i < vocabSize; i++) {
            cumulative += probs[i];
            if (rand <= cumulative) {
                return (byte) i;
            }
        }

        // Fallback (should not reach here)
        return (byte) (vocabSize - 1);
    }

    /**
     * Observe a transition and update counts.
     */
    public void observeTransition(short goalId, int contextHash, byte prev2, byte prev1, byte action) {
        MarkovKey key = new MarkovKey(goalId, contextHash, prev2, prev1);

        transitions.compute(key, (k, stats) -> {
            if (stats == null) {
                stats = new MarkovStats(40); // 40 possible actions
            }
            stats.counts[action & 0xFF]++;
            stats.total++;
            return stats;
        });
    }

    /**
     * Compute context hash from state (bucketized).
     */
    private int computeContextHash(State context) {
        int hash = 17;
        hash = 31 * hash + (context.getBotHealth() / 5); // Health buckets of 5
        hash = 31 * hash + (context.getBotHungerLevel() / 5); // Hunger buckets
        hash = 31 * hash + (context.getTimeOfDay().equals("night") ? 1 : 0);
        hash = 31 * hash + (context.isInDangerousStructure() ? 1 : 0);
        // Add more context features as needed
        return hash;
    }

    /**
     * Check if action is pointless in current context.
     */
    private boolean isPointlessAction(State context, byte actionId) {
        int id = actionId & 0xFF;

        // Don't eat if hunger is full
        if (id == 22 && context.getBotHungerLevel() >= 19) {
            return true;
        }

        // Don't sprint if already sprinting (would need more state tracking)
        // Don't shield if no enemies nearby
        if (id == 12 && context.getNearbyEntities().stream()
                .noneMatch(e -> e.isHostile())) {
            return true;
        }

        // Don't idle too much (limit consecutive idles)
        // This would need sequence history

        return false;
    }

    /**
     * Check if action is terminal (ends the sequence).
     */
    private boolean isTerminalAction(byte actionId) {
        // No terminal actions in this system yet
        // Could add "goal_reached" action later
        return false;
    }

    /**
     * Save Markov data to disk.
     */
    public void saveToDisk() {
        try {
            Path saveDir = Paths.get(SAVE_DIR);
            if (!Files.exists(saveDir)) {
                Files.createDirectories(saveDir);
            }

            String filename = String.format("%s/markov_chain_%d.dat", SAVE_DIR, System.currentTimeMillis());
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new FileOutputStream(filename))) {
                oos.writeObject(new HashMap<>(transitions));
                LOGGER.info("Saved Markov chain to: {}", filename);
            }

        } catch (IOException e) {
            LOGGER.error("Failed to save Markov chain", e);
        }
    }

    /**
     * Load Markov data from disk.
     */
    @SuppressWarnings("unchecked")
    private void loadFromDisk() {
        try {
            Path saveDir = Paths.get(SAVE_DIR);
            if (!Files.exists(saveDir)) {
                return;
            }

            // Find most recent file
            File[] files = saveDir.toFile().listFiles((dir, name) ->
                    name.startsWith("markov_chain_") && name.endsWith(".dat"));

            if (files == null || files.length == 0) {
                LOGGER.info("No existing Markov data found");
                return;
            }

            // Sort by modification time (most recent first)
            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            File latest = files[0];

            try (ObjectInputStream ois = new ObjectInputStream(
                    new FileInputStream(latest))) {
                Map<MarkovKey, MarkovStats> loaded =
                        (Map<MarkovKey, MarkovStats>) ois.readObject();
                transitions.putAll(loaded);
                LOGGER.info("Loaded Markov chain from: {} ({} entries)",
                        latest.getName(), transitions.size());
            }

        } catch (IOException | ClassNotFoundException e) {
            LOGGER.error("Failed to load Markov chain", e);
        }
    }

    /**
     * Get statistics for debugging.
     */
    public String getStats() {
        return String.format("Markov transitions: %d entries, %d actions registered",
                transitions.size(), actionRegistry.size());
    }

    // ===== INNER CLASSES =====

    /**
     * Markov key: (goalId, contextHash, prev2, prev1).
     */
    private static class MarkovKey implements Serializable {
        private static final long serialVersionUID = 1L;

        final short goalId;
        final int contextHash;
        final byte prev2;
        final byte prev1;

        MarkovKey(short goalId, int contextHash, byte prev2, byte prev1) {
            this.goalId = goalId;
            this.contextHash = contextHash;
            this.prev2 = prev2;
            this.prev1 = prev1;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MarkovKey)) return false;
            MarkovKey key = (MarkovKey) o;
            return goalId == key.goalId &&
                   contextHash == key.contextHash &&
                   prev2 == key.prev2 &&
                   prev1 == key.prev1;
        }

        @Override
        public int hashCode() {
            return Objects.hash(goalId, contextHash, prev2, prev1);
        }
    }

    /**
     * Markov statistics: action counts.
     */
    private static class MarkovStats implements Serializable {
        private static final long serialVersionUID = 1L;

        final int[] counts; // counts[actionId] = frequency
        int total;          // sum of all counts

        MarkovStats(int vocabSize) {
            this.counts = new int[vocabSize];
            this.total = 0;
        }
    }
}

