package net.shasankp000.GameAI.planner;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps action IDs (bytes) to action names and vice versa.
 */
public class ActionMapper {

    private static final Map<Byte, String> ID_TO_NAME = new HashMap<>();
    private static final Map<String, Byte> NAME_TO_ID = new HashMap<>();

    static {
        // Movement (1-7)
        register((byte)1, "move_forward");
        register((byte)2, "move_backward");
        register((byte)3, "turn_left");
        register((byte)4, "turn_right");
        register((byte)5, "jump");
        register((byte)6, "sneak");
        register((byte)7, "sprint");

        // Combat (10-13)
        register((byte)10, "attack");
        register((byte)11, "shoot_arrow");
        register((byte)12, "use_shield");
        register((byte)13, "evade");

        // Utility (20-25)
        register((byte)20, "mine_block");
        register((byte)21, "place_block");
        register((byte)22, "eat_food");
        register((byte)23, "equip_armor");
        register((byte)24, "craft_item");
        register((byte)25, "use_torch");

        // Hotbar (31-39)
        for (byte i = 31; i <= 39; i++) {
            register(i, "hotbar_" + (i - 30));
        }
    }

    private static void register(byte id, String name) {
        ID_TO_NAME.put(id, name);
        NAME_TO_ID.put(name, id);
    }

    public static String getActionName(byte id) {
        return ID_TO_NAME.getOrDefault(id, "unknown");
    }

    public static byte getActionId(String name) {
        return NAME_TO_ID.getOrDefault(name, (byte)0);
    }

    public static boolean isValidAction(byte id) {
        return ID_TO_NAME.containsKey(id);
    }
}

