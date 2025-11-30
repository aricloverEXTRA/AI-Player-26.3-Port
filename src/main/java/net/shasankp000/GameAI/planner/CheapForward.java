package net.shasankp000.GameAI.planner;

import net.shasankp000.GameAI.State;

/**
 * Ultra-lightweight forward simulator for plan ranking.
 * NOT ACCURATE - only for relative comparison of plans.
 */
public class CheapForward {

    /**
     * Fake state with minimal information.
     */
    public static class FakeState {
        int hp;
        int hunger;
        boolean hasWood;
        boolean hasStone;
        boolean hasFood;
        boolean hasWeapon;
        boolean hasTool;
        int timeCost;

        FakeState(State realState) {
            this.hp = realState.getBotHealth();
            this.hunger = realState.getBotHungerLevel();

            // Inventory signatures (simplified)
            this.hasWood = realState.getHotBarItems().stream()
                .anyMatch(item -> item.contains("log") || item.contains("wood"));
            this.hasStone = realState.getHotBarItems().stream()
                .anyMatch(item -> item.contains("stone") || item.contains("cobblestone"));
            this.hasFood = realState.getHotBarItems().stream()
                .anyMatch(item -> item.contains("bread") || item.contains("meat") || item.contains("apple"));
            this.hasWeapon = realState.getHotBarItems().stream()
                .anyMatch(item -> item.contains("sword") || item.contains("axe"));
            this.hasTool = realState.getHotBarItems().stream()
                .anyMatch(item -> item.contains("pickaxe") || item.contains("shovel"));

            this.timeCost = 0;
        }

        FakeState copy() {
            FakeState c = new FakeState();
            c.hp = this.hp;
            c.hunger = this.hunger;
            c.hasWood = this.hasWood;
            c.hasStone = this.hasStone;
            c.hasFood = this.hasFood;
            c.hasWeapon = this.hasWeapon;
            c.hasTool = this.hasTool;
            c.timeCost = this.timeCost;
            return c;
        }

        private FakeState() {} // Private for copy()
    }

    /**
     * Apply action to fake state (extremely simplified).
     */
    public static void applyAction(FakeState state, byte actionId) {
        // Modify state in place for efficiency

        switch (actionId) {
            case 1: case 2: case 3: case 4: // Movement
                state.timeCost += 1;
                state.hunger = Math.max(0, state.hunger - 1);
                break;
            case 7: // Sprint
                state.timeCost += 1;
                state.hunger = Math.max(0, state.hunger - 2);
                break;
            case 10: // Attack
                state.timeCost += 2;
                state.hunger = Math.max(0, state.hunger - 1);
                if (state.hasWeapon) {
                    // Assume successful kill (very optimistic)
                    state.hp = Math.max(0, state.hp - 2); // Take some damage
                } else {
                    state.hp = Math.max(0, state.hp - 5); // More damage without weapon
                }
                break;
            case 11: // Shoot arrow
                state.timeCost += 3;
                break;
            case 20: // Mine block
                state.timeCost += 5;
                state.hunger = Math.max(0, state.hunger - 1);
                if (state.hasTool) {
                    // Assume we get wood or stone (crude)
                    if (!state.hasWood) state.hasWood = true;
                    else if (!state.hasStone) state.hasStone = true;
                }
                break;
            case 22: // Eat food
                if (state.hasFood) {
                    state.hunger = Math.min(20, state.hunger + 6);
                    state.timeCost += 2;
                }
                break;
            case 24: // Craft item
                state.timeCost += 5;
                // Assume we craft weapon or tool if we have materials
                if (state.hasWood && !state.hasWeapon) {
                    state.hasWeapon = true;
                } else if (state.hasWood && !state.hasTool) {
                    state.hasTool = true;
                }
                break;
            default:
                state.timeCost += 1; // Generic action cost
        }
    }

    /**
     * Compute goal progress heuristic (0.0 = no progress, 1.0 = complete).
     */
    public static double computeGoalProgress(FakeState state, short goalId) {
        switch (goalId) {
            case 1: // get_wood
                return state.hasWood ? 1.0 : 0.0;
            case 2: // get_stone
                return state.hasStone ? 1.0 : 0.0;
            case 5: // eat_food
                return state.hunger >= 18 ? 1.0 : (state.hunger / 18.0);
            case 6: // craft_weapon
                return state.hasWeapon ? 1.0 : 0.0;
            default:
                return 0.0; // Unknown goal
        }
    }
}

