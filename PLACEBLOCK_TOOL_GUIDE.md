# PlaceBlock Tool - User Guide

## Overview

The **placeBlock** tool enables the AI bot to place blocks in the Minecraft world through intelligent function calling. It handles all the complexity of inventory management, validation, and placement mechanics automatically.

---

## Features

### 🏗️ **Smart Block Placement**
- Places blocks at any valid coordinate within reach
- Automatically handles Minecraft's placement mechanics
- Verifies successful placement

### 📦 **Automatic Inventory Management**
- Searches entire inventory for requested block
- Moves block to hotbar automatically if needed
- Preserves existing hotbar layout when possible

### ✅ **Comprehensive Validation**
- **Distance Check:** Ensures bot is within 5 blocks of target
- **Inventory Check:** Confirms block is available
- **Position Check:** Validates target is empty/replaceable
- **Surface Check:** Finds suitable adjacent block to place against

### 🎯 **Precise Execution**
- Bot automatically looks at placement target
- Uses correct placement direction based on adjacent blocks
- Handles edge cases gracefully

---

## How to Use

### Basic Example

```
Player: "Place a stone block at x:100, y:64, z:200"
```

The AI will:
1. Parse the request
2. Find "stone" in the bot's inventory
3. Move close enough if needed (via goTo)
4. Execute placement
5. Confirm success

### Advanced Example: Building a Wall

```
Player: "Build a 5-block wide cobblestone wall starting at x:50, y:65, z:-30"
```

The AI can chain multiple placeBlock calls in a pipeline:
```json
{
  "pipeline": [
    {"functionName": "placeBlock", "parameters": [
      {"parameterName": "targetX", "parameterValue": "50"},
      {"parameterName": "targetY", "parameterValue": "65"},
      {"parameterName": "targetZ", "parameterValue": "-30"},
      {"parameterName": "blockType", "parameterValue": "cobblestone"}
    ]},
    {"functionName": "placeBlock", "parameters": [
      {"parameterName": "targetX", "parameterValue": "51"},
      {"parameterName": "targetY", "parameterValue": "65"},
      {"parameterName": "targetZ", "parameterValue": "-30"},
      {"parameterName": "blockType", "parameterValue": "cobblestone"}
    ]},
    // ... more blocks
  ]
}
```

### Using Placeholders

Combine with detectBlocks to place relative to detected positions:

```
Player: "Find the nearest oak log and place dirt on top of it"
```

Pipeline:
```json
{
  "pipeline": [
    {"functionName": "detectBlocks", "parameters": [
      {"parameterName": "blockType", "parameterValue": "oak_log"}
    ]},
    {"functionName": "placeBlock", "parameters": [
      {"parameterName": "targetX", "parameterValue": "$lastDetectedBlock.x"},
      {"parameterName": "targetY", "parameterValue": "$lastDetectedBlock.y+1"},
      {"parameterName": "targetZ", "parameterValue": "$lastDetectedBlock.z"},
      {"parameterName": "blockType", "parameterValue": "dirt"}
    ]}
  ]
}
```

---

## Parameters

| Parameter | Type | Description | Example |
|-----------|------|-------------|---------|
| `targetX` | Integer | X coordinate where block should be placed | `100` |
| `targetY` | Integer | Y coordinate where block should be placed | `64` |
| `targetZ` | Integer | Z coordinate where block should be placed | `-50` |
| `blockType` | String | Type of block to place | `"stone"`, `"minecraft:oak_planks"`, `"dirt"` |

### Block Type Format

The tool accepts block names in two formats:
- **Short form:** `"stone"`, `"dirt"`, `"oak_log"`
- **Full form:** `"minecraft:stone"`, `"minecraft:dirt"`

The system automatically normalizes to the full form internally.

---

## Return Values

The tool updates shared state with placement information:

```javascript
{
  "lastPlacedBlock.x": 100,
  "lastPlacedBlock.y": 64,
  "lastPlacedBlock.z": -50,
  "lastPlacedBlock.type": "minecraft:stone"
}
```

These values can be used in subsequent pipeline steps with `$placeholders`.

---

## Success Messages

✅ **"Successfully placed [block] at x:X y:Y z:Z"**
- Block was placed and verified
- Coordinates show exact placement location

---

## Error Messages

### ❌ "Too far from target position!"
**Cause:** Bot is more than 5 blocks away from target

**Solution:** Chain with `goTo` to move bot closer first:
```json
{
  "pipeline": [
    {"functionName": "goTo", "parameters": [
      {"parameterName": "x", "parameterValue": "100"},
      {"parameterName": "y", "parameterValue": "64"},
      {"parameterName": "z", "parameterValue": "200"},
      {"parameterName": "sprint", "parameterValue": "true"}
    ]},
    {"functionName": "placeBlock", "parameters": [
      {"parameterName": "targetX", "parameterValue": "100"},
      {"parameterName": "targetY", "parameterValue": "65"},
      {"parameterName": "targetZ", "parameterValue": "200"},
      {"parameterName": "blockType", "parameterValue": "stone"}
    ]}
  ]
}
```

### ❌ "Block not found in inventory: [block]"
**Cause:** The requested block is not in the bot's inventory

**Solution:** 
- Give the bot the required blocks
- Use a different block type that the bot has
- First mine/collect the needed blocks

### ❌ "Target position is already occupied by: [block]"
**Cause:** There's already a block at the target coordinates

**Solution:**
- Mine the existing block first with `mineBlock`
- Choose a different target position
- Check if the target is truly empty

### ❌ "No suitable surface found to place block against at [pos]"
**Cause:** Minecraft requires blocks to be placed against an adjacent solid block, but none was found

**Solution:**
- Place an adjacent block first to create a surface
- Choose a target position next to existing blocks
- Build up from the ground instead of placing in mid-air

### ⚠️ "Block placement appeared to succeed but block is not present at target position"
**Cause:** Placement command executed but verification failed (rare edge case)

**Solution:**
- Retry the placement
- Check if there are environmental factors preventing placement
- Verify coordinates are correct

---

## Best Practices

### 1. **Always Check Distance**
If placing multiple blocks, use `goTo` to position the bot optimally:
```json
{"functionName": "goTo", "parameters": [
  {"parameterName": "x", "parameterValue": "100"},
  {"parameterName": "y", "parameterValue": "64"},
  {"parameterName": "z", "parameterValue": "200"},
  {"parameterName": "sprint", "parameterValue": "false"}
]}
```

### 2. **Build from Ground Up**
When building structures, place blocks from bottom to top to ensure surfaces are always available.

### 3. **Use Placeholders for Relative Placement**
Leverage `$lastDetectedBlock` or `$lastPlacedBlock` values for building relative to detected positions.

### 4. **Batch Similar Operations**
Group multiple placements in a pipeline for efficiency:
```json
{
  "pipeline": [
    {"functionName": "goTo", ...},
    {"functionName": "placeBlock", ...},
    {"functionName": "placeBlock", ...},
    {"functionName": "placeBlock", ...}
  ]
}
```

### 5. **Verify Inventory First**
For large building tasks, ensure the bot has sufficient blocks before starting.

---

## Technical Details

### Maximum Placement Distance
**5.0 blocks** - This matches Minecraft's player reach distance

### Execution Model
- **Asynchronous:** Runs in CompletableFuture
- **Timeout:** 10 seconds per placement
- **Thread-Safe:** Can be called from any thread

### Placement Mechanics
The tool follows Minecraft's vanilla placement rules:
1. Finds an adjacent solid block (checks all 6 directions)
2. Calculates hit position on that block's face
3. Uses player interaction manager to place
4. Verifies block state changed

### Hotbar Management
- Searches slots 0-8 (hotbar) first
- Falls back to main inventory slots 9-35
- Moves to empty hotbar slot if available
- Uses slot 8 as fallback if hotbar is full

---

## Example Use Cases

### 1. Repair a Wall Gap
```
Player: "Fix the gap in the wall by placing cobblestone at x:45, y:68, z:12"
```

### 2. Build a Floor
```
Player: "Place oak planks to make a 3x3 floor starting at x:0, y:64, z:0"
```

### 3. Mark a Location
```
Player: "Place a torch at x:100, y:70, z:-200 to mark this spot"
```

### 4. Create a Bridge
```
Player: "Build a bridge from here to x:50, y:65, z:30 using stone"
```

### 5. Place Blocks Relative to Detection
```
Player: "Find the nearest chest and place a sign next to it"
```

---

## Limitations

1. **Reach Distance:** Bot must be within 5 blocks of target
2. **Adjacent Block Required:** Cannot place blocks in mid-air without a surface
3. **Inventory Requirement:** Block must exist in bot's inventory
4. **Replaceable Blocks Only:** Target position must be empty or contain a replaceable block (air, water, tall grass, etc.)
5. **No Physics Simulation:** Doesn't check if block will fall (e.g., sand, gravel)

---

## Integration with Other Tools

### Combining with goTo
```json
{
  "pipeline": [
    {"functionName": "goTo", "parameters": [...]},
    {"functionName": "placeBlock", "parameters": [...]}
  ]
}
```

### Combining with detectBlocks
```json
{
  "pipeline": [
    {"functionName": "detectBlocks", "parameters": [...]},
    {"functionName": "placeBlock", "parameters": [
      {"parameterName": "targetX", "parameterValue": "$lastDetectedBlock.x"},
      ...
    ]}
  ]
}
```

### Combining with mineBlock
```json
{
  "pipeline": [
    {"functionName": "mineBlock", "parameters": [...]},
    {"functionName": "placeBlock", "parameters": [...]}
  ]
}
```

---

## Troubleshooting

### Problem: Bot doesn't move close enough
**Solution:** Adjust goTo coordinates to be within 3 blocks of placement target (goTo has ~3 block stopping distance)

### Problem: "No suitable surface" error
**Solution:** Place a temporary scaffolding block first to create a surface, then place the desired block

### Problem: Wrong block placed
**Solution:** Check block type spelling - use F3 debug menu in-game to see exact block IDs

### Problem: Block appears but disappears immediately
**Solution:** This may indicate a conflict with world generation or other mods - verify in vanilla Minecraft

---

## Future Enhancements

Potential improvements for future versions:
- [ ] Auto-scaffolding for mid-air placement
- [ ] Multi-block structure templates
- [ ] Rotation/orientation control for directional blocks
- [ ] Undo/rollback functionality
- [ ] Safety checks for structural integrity
- [ ] Support for NBT data (signs with text, etc.)

---

## See Also

- [Function Calling System Documentation](FUNCTION_CALLING.md)
- [Tool Registry](src/main/java/net/shasankp000/FunctionCaller/ToolRegistry.java)
- [Block Placement Tool Implementation](src/main/java/net/shasankp000/PlayerUtils/BlockPlacementTool.java)

