package net.shasankp000.GameAI.planner;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps goal descriptions to goal IDs and vice versa.
 */
public class GoalMapper {

    private static final Map<String, Short> GOAL_NAME_TO_ID = new HashMap<>();
    private static final Map<Short, String> GOAL_ID_TO_NAME = new HashMap<>();

    static {
        // Register all goals
        registerGoal((short) 1, "get_wood", "wood", "log", "tree");
        registerGoal((short) 2, "get_stone", "stone", "cobblestone", "mine");
        registerGoal((short) 3, "survive_night", "survive", "night", "shelter");
        registerGoal((short) 4, "kill_hostile", "kill", "hostile", "mob", "enemy");
        registerGoal((short) 5, "eat_food", "eat", "food", "hunger");
        registerGoal((short) 6, "craft_weapon", "craft", "weapon", "sword", "tool");
        registerGoal((short) 7, "equip_armor", "equip", "armor", "protect");
    }

    private static void registerGoal(short id, String primaryName, String... aliases) {
        GOAL_NAME_TO_ID.put(primaryName, id);
        GOAL_ID_TO_NAME.put(id, primaryName);

        for (String alias : aliases) {
            GOAL_NAME_TO_ID.put(alias.toLowerCase(), id);
        }
    }

    /**
     * Parse a goal description to get goal ID.
     *
     * @param goalDescription Natural language goal (e.g., "get some wood")
     * @return Goal ID (1-7), or 0 if not recognized
     */
    public static short parseGoal(String goalDescription) {
        if (goalDescription == null || goalDescription.isEmpty()) {
            return 0;
        }

        String normalized = goalDescription.toLowerCase().trim();

        // Direct match
        Short directMatch = GOAL_NAME_TO_ID.get(normalized);
        if (directMatch != null) {
            return directMatch;
        }

        // Keyword match
        for (Map.Entry<String, Short> entry : GOAL_NAME_TO_ID.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return 0; // Unknown goal
    }

    /**
     * Get goal name from ID.
     */
    public static String getGoalName(short goalId) {
        return GOAL_ID_TO_NAME.getOrDefault(goalId, "unknown");
    }

    /**
     * Check if a goal ID is valid.
     */
    public static boolean isValidGoal(short goalId) {
        return goalId >= 1 && goalId <= 7;
    }
}

