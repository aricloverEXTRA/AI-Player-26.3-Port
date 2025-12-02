package net.shasankp000.GameAI.planner;

import java.util.*;

/**
 * Maps natural language goals to numeric goal IDs for the Markov planner.
 */
public class GoalMapper {
    private static final Map<Short, String> GOAL_ID_TO_NAME = new HashMap<>();
    private static final Map<String, Short> GOAL_KEYWORD_TO_ID = new HashMap<>();

    // Goal IDs (0-255 for now, expand later if needed)
    public static final short GOAL_MINE = 1;
    public static final short GOAL_BUILD = 2;
    public static final short GOAL_CRAFT = 3;
    public static final short GOAL_NAVIGATE = 4;
    public static final short GOAL_COMBAT = 5;
    public static final short GOAL_GATHER = 6;
    public static final short GOAL_EXPLORE = 7;
    public static final short GOAL_FARM = 8;
    public static final short GOAL_TRADE = 9;
    public static final short GOAL_UNKNOWN = 0;

    static {
        // Register goal mappings
        registerGoal(GOAL_MINE, "mine", "mining", "dig", "excavate", "extract");
        registerGoal(GOAL_BUILD, "build", "place", "construct", "create structure");
        registerGoal(GOAL_CRAFT, "craft", "make", "create", "assemble");
        registerGoal(GOAL_NAVIGATE, "go", "navigate", "move", "travel", "walk");
        registerGoal(GOAL_COMBAT, "fight", "attack", "combat", "kill", "defend");
        registerGoal(GOAL_GATHER, "gather", "collect", "fetch", "get", "obtain");
        registerGoal(GOAL_EXPLORE, "explore", "search", "find", "look for");
        registerGoal(GOAL_FARM, "farm", "harvest", "plant", "grow");
        registerGoal(GOAL_TRADE, "trade", "buy", "sell", "exchange");
    }

    /**
     * Register a goal with multiple keyword triggers.
     */
    private static void registerGoal(short goalId, String name, String... keywords) {
        GOAL_ID_TO_NAME.put(goalId, name);
        for (String keyword : keywords) {
            GOAL_KEYWORD_TO_ID.put(keyword.toLowerCase(), goalId);
        }
    }

    /**
     * Parse a natural language goal into a goal ID.
     * Uses keyword matching on the input string.
     */
    public static short parseGoal(String naturalLanguageGoal) {
        if (naturalLanguageGoal == null || naturalLanguageGoal.isEmpty()) {
            return GOAL_UNKNOWN;
        }

        String lower = naturalLanguageGoal.toLowerCase();

        // Try to find matching keywords
        for (Map.Entry<String, Short> entry : GOAL_KEYWORD_TO_ID.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return GOAL_UNKNOWN;
    }

    /**
     * Get the human-readable name for a goal ID.
     */
    public static String getGoalName(short goalId) {
        return GOAL_ID_TO_NAME.getOrDefault(goalId, "unknown");
    }

    /**
     * Get all registered goal IDs.
     */
    public static short[] getAllGoalIds() {
        List<Short> sortedIds = new ArrayList<>(GOAL_ID_TO_NAME.keySet());
        Collections.sort(sortedIds);
        short[] result = new short[sortedIds.size()];
        for (int i = 0; i < sortedIds.size(); i++) {
            result[i] = sortedIds.get(i);
        }
        return result;
    }


    /**
     * Check if a goal ID is valid.
     */
    public static boolean isValidGoal(short goalId) {
        return GOAL_ID_TO_NAME.containsKey(goalId);
    }
}

