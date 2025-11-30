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
import java.util.concurrent.ThreadLocalRandom;

/**
 * 2nd-order Markov Chain for action sequence learning.
 * Enhanced with full state access and parallel processing support.
 */
public class MarkovChain2 {
    private static final Logger LOGGER = LoggerFactory.getLogger("MarkovChain2");

    // Smoothing parameter for unseen transitions
    private static final double SMOOTHING_ALPHA = 1.0;

    // Thread-safe storage: (goalId, stateHash, prev2, prev1) -> action counts
    private final ConcurrentHashMap<MarkovKey, MarkovStats> transitions;

    // Storage directory
    private static final String STORAGE_DIR = "markov_data";

    public MarkovChain2() {
        this.transitions = new ConcurrentHashMap<>();
        loadFromDisk();
    }

    /**
     * Generate initial action sequence using learned probabilities.
     * Enhanced with full state context for better decisions.
     */
    public List<PlannedStep> draftPlan(short goalId, State state, int maxLen, double epsilon) {
        List<PlannedStep> plan = new ArrayList<>();

        // Get state signature
        int stateHash = computeStateSignature(state);

        // Start with special actions (0 = START)
        byte prev2 = 0;
        byte prev1 = 0;

        for (int i = 0; i < maxLen; i++) {
            // Context-aware exploration
            byte nextAction;
            if (ThreadLocalRandom.current().nextDouble() < epsilon) {
                // Explore: choose context-aware random action
                nextAction = getContextAwareRandomAction(goalId, state);
            } else {
                // Exploit: sample from learned distribution
                nextAction = sampleAction(goalId, stateHash, prev2, prev1);
            }

            // Check if action is pointless given current state
            if (isPointless(nextAction, state)) {
                continue; // Skip pointless actions
            }

            // Add to plan
            String actionName = ActionMapper.getActionName(nextAction);
            plan.add(new PlannedStep(nextAction, actionName, 0.0, ""));

            // Update history
            prev2 = prev1;
            prev1 = nextAction;

            // Check if goal seems complete
            if (seemsComplete(goalId, state, plan)) {
                break;
            }
        }

        return plan;
    }

    /**
     * Sample next action from learned distribution with add-1 smoothing.
     */
    private byte sampleAction(short goalId, int stateHash, byte prev2, byte prev1) {
        MarkovKey key = new MarkovKey(goalId, stateHash, prev2, prev1);
        MarkovStats stats = transitions.get(key);

        // Get all possible actions (0-39)
        int numActions = 40;
        double[] probs = new double[numActions];
        double total = 0.0;

        if (stats != null) {
            // Use learned counts + smoothing
            for (int a = 0; a < numActions; a++) {
                probs[a] = stats.counts[a] + SMOOTHING_ALPHA;
                total += probs[a];
            }
        } else {
            // Uniform with smoothing (never seen this state)
            for (int a = 0; a < numActions; a++) {
                probs[a] = SMOOTHING_ALPHA;
                total += probs[a];
            }
        }

        // Normalize
        for (int a = 0; a < numActions; a++) {
            probs[a] /= total;
        }

        // Sample
        double rand = ThreadLocalRandom.current().nextDouble();
        double cumulative = 0.0;
        for (int a = 0; a < numActions; a++) {
            cumulative += probs[a];
            if (rand < cumulative) {
                return (byte) a;
            }
        }

        return 0; // Fallback (should never happen)
    }

    /**
     * Get context-aware random action based on goal and state.
     */
    private byte getContextAwareRandomAction(short goalId, State state) {
        List<Byte> candidates = new ArrayList<>();

        // Goal-specific action preferences
        switch (goalId) {
            case 1: // get_wood
                candidates.addAll(Arrays.asList((byte)1, (byte)20, (byte)7)); // move, mine, sprint
                break;
            case 2: // get_stone
                candidates.addAll(Arrays.asList((byte)1, (byte)20, (byte)7));
                break;
            case 3: // survive_night
                candidates.addAll(Arrays.asList((byte)25, (byte)6, (byte)12)); // torch, sneak, shield
                break;
            case 4: // kill_hostile
                candidates.addAll(Arrays.asList((byte)10, (byte)11, (byte)12)); // attack, shoot, shield
                break;
            case 5: // eat_food
                candidates.add((byte)22); // eat
                break;
            case 6: // craft_weapon
                candidates.add((byte)24); // craft
                break;
            case 7: // equip_armor
                candidates.add((byte)23); // equip_armor
                break;
            default:
                // Fallback: all movement actions
                for (byte a = 1; a <= 7; a++) {
                    candidates.add(a);
                }
        }

        // Filter out pointless actions
        candidates.removeIf(a -> isPointless(a, state));

        if (candidates.isEmpty()) {
            // Return safe default (move forward)
            return 1;
        }

        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    /**
     * Check if action is pointless given current state (full state access).
     */
    private boolean isPointless(byte action, State state) {
        switch (action) {
            case 22: // eat_food
                return state.getBotHungerLevel() >= 18; // Don't eat if nearly full
            case 12: // use_shield
                // Don't shield if no nearby threats
                return state.getNearbyEntities().stream()
                    .noneMatch(e -> e.isHostile() &&
                Math.sqrt(Math.pow(e.getX() - state.getBotX(), 2) +
                        Math.pow(e.getZ() - state.getBotZ(), 2)) < 10.0);
            case 10: // attack
            case 11: // shoot_arrow
                // Don't attack if no hostiles nearby
                return state.getNearbyEntities().stream()
                    .noneMatch(e -> e.isHostile());
            case 20: // mine_block
                // Don't mine without tools (simplified - check main hand)
                String heldItem = state.getSelectedItem();
                return heldItem == null || heldItem.equals("Air");
            case 7: // sprint
                return state.getBotHungerLevel() < 6; // Don't sprint if too hungry
            default:
                return false;
        }
    }

    /**
     * Check if goal seems complete based on state and plan executed so far.
     */
    private boolean seemsComplete(short goalId, State state, List<PlannedStep> planSoFar) {
        switch (goalId) {
            case 1: // get_wood
                // Check if we have wood in inventory
                return state.getHotBarItems().stream().anyMatch(item ->
                    item.contains("log") || item.contains("wood") || item.contains("planks"));
            case 4: // kill_hostile
                // Check if no more hostiles nearby
                return state.getNearbyEntities().stream().noneMatch(net.shasankp000.Entity.EntityDetails::isHostile);
            case 5: // eat_food
                // Check if hunger restored
                return state.getBotHungerLevel() >= 18;
            default:
                return false; // Can't determine completion
        }
    }

    /**
     * Compute rich state signature from full state.
     * Uses actual values instead of simple bucketing.
     */
    private int computeStateSignature(State state) {
        // Use Objects.hash for stable hashCode
        return Objects.hash(
            // Health & survival (bucketed for some stability)
            state.getBotHealth() / 5, // 0-4 buckets
            state.getBotHungerLevel() / 5, // 0-4 buckets
            state.getBotOxygenLevel() / 100, // 0-3 buckets

            // Position (already bucketed)
                state.getBotX() / 10,
                state.getBotY() / 10,
                state.getBotZ() / 10,

            // Time & dimension
            state.getTimeOfDay(),
            state.getDimensionType(),

            // Entities (counts by type)
            getHostileCount(state),
            getNeutralCount(state),
            getClosestHostileDistance(state),

            // Equipment
            state.getSelectedItem(),
            state.getOffhandItem(),
            hasFullArmor(state),

            // Inventory categories (bucketed counts)
            getWoodCount(state) / 10,
            getStoneCount(state) / 10,
            getFoodCount(state) / 5,
            getWeaponCount(state),
            getToolCount(state)
        );
    }

    // Helper methods for state analysis
    private long getHostileCount(State state) {
            return state.getNearbyEntities().stream().filter(net.shasankp000.Entity.EntityDetails::isHostile).count();
    }

    private long getNeutralCount(State state) {
        return state.getNearbyEntities().stream().filter(e -> !e.isHostile()).count();
    }

    private double getClosestHostileDistance(State state) {
        return state.getNearbyEntities().stream()
                .filter(net.shasankp000.Entity.EntityDetails::isHostile)
            .mapToDouble(e -> Math.sqrt(
                Math.pow(e.getX() - state.getBotX(), 2) +
                        Math.pow(e.getZ() - state.getBotZ(), 2)
            ))
            .min()
            .orElse(Double.MAX_VALUE);
    }

    private boolean hasFullArmor(State state) {
        Map<String, String> armor = state.getArmorItems();
        return !armor.get("helmet").contains("air") &&
               !armor.get("chestplate").contains("air") &&
               !armor.get("leggings").contains("air") &&
               !armor.get("boots").contains("air");
    }

    private int getWoodCount(State state) {
        return (int) state.getHotBarItems().stream()
            .filter(item -> item.contains("log") || item.contains("wood") || item.contains("planks"))
            .count();
    }

    private int getStoneCount(State state) {
        return (int) state.getHotBarItems().stream()
            .filter(item -> item.contains("stone") || item.contains("cobblestone"))
            .count();
    }

    private int getFoodCount(State state) {
        return (int) state.getHotBarItems().stream()
            .filter(item -> item.contains("bread") || item.contains("meat") ||
                          item.contains("apple") || item.contains("carrot"))
            .count();
    }

    private int getWeaponCount(State state) {
        return (int) state.getHotBarItems().stream()
            .filter(item -> item.contains("sword") || item.contains("axe"))
            .count();
    }

    private int getToolCount(State state) {
        return (int) state.getHotBarItems().stream()
            .filter(item -> item.contains("pickaxe") || item.contains("shovel") || item.contains("axe"))
            .count();
    }

    /**
     * Observe and record a transition for learning.
     */
    public void observeTransition(short goalId, int stateHash, byte prev2, byte prev1, byte action) {
        MarkovKey key = new MarkovKey(goalId, stateHash, prev2, prev1);
        transitions.compute(key, (k, stats) -> {
            if (stats == null) {
                stats = new MarkovStats();
            }
            stats.counts[action]++;
            stats.total++;
            return stats;
        });
    }

    /**
     * Save Markov chain to disk.
     */
    public void saveToDisk() {
        try {
            Path dir = Paths.get(STORAGE_DIR);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            String filename = String.format("markov_chain_%d.dat", System.currentTimeMillis());
            Path file = dir.resolve(filename);

            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(file)))) {
                oos.writeObject(transitions);
            }

            LOGGER.info("Saved Markov chain to: {} ({} transitions)",
                       file.toAbsolutePath(), transitions.size());
        } catch (IOException e) {
            LOGGER.error("Failed to save Markov chain", e);
        }
    }

    /**
     * Load Markov chain from disk (most recent file).
     */
    @SuppressWarnings("unchecked")
    private void loadFromDisk() {
        try {
            Path dir = Paths.get(STORAGE_DIR);
            if (!Files.exists(dir)) {
                LOGGER.info("No existing Markov data found, starting fresh");
                return;
            }

            // Find most recent .dat file
            Optional<Path> latest = Files.list(dir)
                .filter(p -> p.toString().endsWith(".dat"))
                .max(Comparator.comparingLong(p -> p.toFile().lastModified()));

            if (latest.isPresent()) {
                try (ObjectInputStream ois = new ObjectInputStream(
                        new BufferedInputStream(Files.newInputStream(latest.get())))) {
                    ConcurrentHashMap<MarkovKey, MarkovStats> loaded =
                        (ConcurrentHashMap<MarkovKey, MarkovStats>) ois.readObject();
                    transitions.putAll(loaded);
                    LOGGER.info("Loaded Markov chain from: {} ({} transitions)",
                               latest.get().toAbsolutePath(), transitions.size());
                }
            } else {
                LOGGER.info("No Markov data files found, starting fresh");
            }
        } catch (IOException | ClassNotFoundException e) {
            LOGGER.error("Failed to load Markov chain", e);
        }
    }

    /**
     * Get number of learned transitions.
     */
    public int size() {
        return transitions.size();
    }

    // Inner classes

    /**
     * Key for Markov chain: (goalId, stateHash, prev2, prev1)
     */
    static class MarkovKey implements Serializable {
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
    }

    /**
     * Statistics for action distribution: counts per action.
     */
    static class MarkovStats implements Serializable {
        private static final long serialVersionUID = 1L;

        final int[] counts = new int[40]; // 40 possible actions
        int total = 0;
    }
}

