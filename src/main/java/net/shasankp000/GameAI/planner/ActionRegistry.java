package net.shasankp000.GameAI.planner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of all available actions for the planner.
 * Maps action IDs to action metadata and provides sampling utilities.
 */
public class ActionRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("planner");

    private final Map<Byte, ActionInfo> actions;
    private final List<Byte> actionIds;
    private final Map<String, Byte> nameToId;

    public ActionRegistry() {
        this.actions = new ConcurrentHashMap<>();
        this.actionIds = new ArrayList<>();
        this.nameToId = new ConcurrentHashMap<>();

        // Register all available actions
        registerDefaultActions();
    }

    /**
     * Register all default actions from FunctionCallerV2.
     */
    private void registerDefaultActions() {
        // Movement actions (low time cost)
        register(1, "moveToCoordinates", "Move bot to specific coordinates",
                new String[]{"x", "y", "z"}, 5.0, false, 5);
        register(2, "moveToEntity", "Move bot towards an entity",
                new String[]{"entityName"}, 3.0, false, 5);
        register(3, "retreat", "Move away from danger",
                new String[]{}, 2.0, false, 8);

        // Combat actions
        register(10, "attack", "Attack nearest hostile entity",
                new String[]{}, 1.0, false, 7);
        register(11, "shoot", "Shoot arrow at target",
                new String[]{"target"}, 2.0, false, 7);
        register(12, "evade", "Dodge incoming projectile",
                new String[]{}, 1.5, false, 9);

        // Block interaction
        register(20, "mineBlock", "Mine a block at coordinates",
                new String[]{"x", "y", "z"}, 3.0, false, 6);
        register(21, "placeBlock", "Place a block",
                new String[]{"blockType", "x", "y", "z"}, 1.0, false, 4);
        register(22, "breakNearestBlock", "Break nearest matching block",
                new String[]{"blockType"}, 2.0, false, 6);

        // Item management
        register(30, "craftItem", "Craft an item",
                new String[]{"itemName", "count"}, 4.0, false, 5);
        register(31, "smeltItem", "Smelt items in furnace",
                new String[]{"inputItem", "count"}, 10.0, false, 4);
        register(32, "equipItem", "Equip item from inventory",
                new String[]{"itemName", "slot"}, 0.5, false, 6);
        register(33, "dropItem", "Drop item from inventory",
                new String[]{"itemName", "count"}, 0.5, false, 3);

        // Survival actions
        register(40, "eat", "Consume food to restore hunger",
                new String[]{}, 1.0, false, 8);
        register(41, "sleep", "Sleep in a bed",
                new String[]{}, 5.0, false, 5);
        register(42, "shield", "Block with shield",
                new String[]{}, 0.5, false, 9);

        // Information gathering
        register(50, "scanArea", "Scan nearby area for entities/blocks",
                new String[]{"radius"}, 0.5, false, 4);
        register(51, "findPath", "Calculate path to destination",
                new String[]{"x", "y", "z"}, 1.0, false, 5);

        // Goal completion
        register(60, "fetchItem", "Fetch specific item and bring to player",
                new String[]{"itemName", "count"}, 8.0, true, 10);
        register(61, "buildStructure", "Build a structure",
                new String[]{"structureType", "x", "y", "z"}, 15.0, true, 6);
        register(62, "gatherResources", "Gather specific resources",
                new String[]{"resourceType", "count"}, 12.0, true, 7);

        LOGGER.info("Registered {} actions in ActionRegistry", actions.size());
    }

    /**
     * Register a new action.
     */
    public void register(int id, String name, String description,
                        String[] paramNames, double estimatedTime,
                        boolean isTerminal, int priority) {
        byte byteId = (byte) id;
        ActionInfo info = new ActionInfo(byteId, name, description,
                                        paramNames, estimatedTime,
                                        isTerminal, priority);
        actions.put(byteId, info);
        actionIds.add(byteId);
        nameToId.put(name.toLowerCase(), byteId);
    }

    /**
     * Get action info by ID.
     */
    public ActionInfo getActionInfo(byte actionId) {
        return actions.get(actionId);
    }

    /**
     * Get action ID by name.
     */
    public Byte getActionId(String actionName) {
        return nameToId.get(actionName.toLowerCase());
    }

    /**
     * Sample a random action (weighted by priority).
     */
    public byte sampleRandomAction(Random random) {
        if (actionIds.isEmpty()) return 0;

        // Weight by priority
        int totalPriority = actions.values().stream()
            .mapToInt(a -> a.priority)
            .sum();

        int r = random.nextInt(totalPriority);
        int cumulative = 0;

        for (byte id : actionIds) {
            ActionInfo info = actions.get(id);
            cumulative += info.priority;
            if (r < cumulative) {
                return id;
            }
        }

        return actionIds.get(0); // Fallback
    }

    /**
     * Get all action IDs.
     */
    public List<Byte> getAllActionIds() {
        return new ArrayList<>(actionIds);
    }

    /**
     * Get all actions.
     */
    public Collection<ActionInfo> getAllActions() {
        return actions.values();
    }

    /**
     * Get actions matching a goal keyword.
     */
    public List<ActionInfo> getActionsForGoal(String goalKeyword) {
        List<ActionInfo> matching = new ArrayList<>();
        String keyword = goalKeyword.toLowerCase();

        for (ActionInfo info : actions.values()) {
            if (info.name.toLowerCase().contains(keyword) ||
                info.description.toLowerCase().contains(keyword)) {
                matching.add(info);
            }
        }

        // Sort by priority
        matching.sort((a, b) -> Integer.compare(b.priority, a.priority));
        return matching;
    }

    /**
     * Get statistics.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalActions", actions.size());
        stats.put("terminalActions", actions.values().stream()
            .filter(a -> a.isTerminal)
            .count());
        return stats;
    }
}

