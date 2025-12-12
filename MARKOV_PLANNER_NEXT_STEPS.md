# Markov Planner - Next Steps & Debugging Guide

**Date**: December 12, 2025  
**Status**: 90% Complete - Core functionality working, execution needs debugging

---

## ✅ WHAT'S WORKING

### 1. **Plan Generation Pipeline**
- ✅ Goal parsing (keyword matching) in <1ms
- ✅ Markov chain generates 3-6 step plans in 30-40ms
- ✅ Risk analysis scores plans correctly
- ✅ Beam search refinement improves plan quality
- ✅ Sequential execution using CompletableFuture chains

### 2. **Action Registry**
- ✅ Automatically loads all tools from ToolRegistry
- ✅ Bidirectional byte <-> function name mapping
- ✅ Goal-specific action filtering (gather, build, combat, etc.)

### 3. **Logging & Monitoring**
- ✅ Detailed debug logs at each step
- ✅ Plan visualization in console
- ✅ Success/failure tracking per step

---

## ⚠️ WHAT NEEDS FIXING

### **CRITICAL: Actions Not Executing In-Game**

**Symptom**: Plans execute and log "✓ completed" but bot doesn't move/mine/build

**Root Cause Analysis**:
1. `callFunction()` is correctly routing to `Tools.*` methods ✅
2. Each tool method logs "Calling method: X" ✅
3. **BUT**: Tool methods may not be sending actual commands to the game

**Hypothesis**: The `Tools` class methods might be placeholders or missing command execution

**Debugging Steps**:

#### Step 1: Check Tools.java Implementation
```bash
# Open Tools.java and verify each method actually sends commands
grep -n "executeWithPrefix" Tools.java
grep -n "getCommandManager" Tools.java
```

Expected to find:
```java
// In Tools.goTo():
server.getCommandManager().executeWithPrefix(
    botSource, 
    "/player " + botName + " goto " + x + " " + y + " " + z
);
```

If this is missing, that's the bug.

#### Step 2: Add Temporary Debug Commands
Add to each Tools method:
```java
public static void goTo(int x, int y, int z, boolean sprint) {
    LOGGER.info("🎯 [TOOLS] goTo called with x={}, y={}, z={}, sprint={}", x, y, z, sprint);
    
    if (botSource == null) {
        LOGGER.error("❌ [TOOLS] botSource is null! Cannot execute command.");
        return;
    }
    
    MinecraftServer server = botSource.getServer();
    if (server == null) {
        LOGGER.error("❌ [TOOLS] server is null!");
        return;
    }
    
    String command = String.format("/player %s goto %d %d %d", botSource.getName(), x, y, z);
    LOGGER.info("🔧 [TOOLS] Executing command: {}", command);
    
    server.getCommandManager().executeWithPrefix(botSource, command);
    
    LOGGER.info("✓ [TOOLS] Command executed successfully");
}
```

#### Step 3: Test Single Action
```bash
/bot goal Move forward 5 blocks
```

Expected logs:
```
🔧 callFunction: goTo with params: {x=105, y=64, z=100, sprint=true}
Calling method: goTo with x=105 y=64 z=100 sprint=true
🎯 [TOOLS] goTo called with x=105, y=64, z=100, sprint=true
🔧 [TOOLS] Executing command: /player Steve goto 105 64 100
✓ [TOOLS] Command executed successfully
✓ Function goTo execution completed
```

If you see "botSource is null", that's the bug location.

---

## 🔧 FIXES TO APPLY

### Fix #1: Ensure botSource is Set Before Plan Execution
**File**: `FunctionCallerV2.java`, `handleGoal()` method

**Check this line exists** (should already be there):
```java
// In handleGoal() before calling planner
botSource = source;
```

### Fix #2: Verify Tools Methods Send Commands
**File**: `Tools.java` (all methods)

**Each method should**:
1. Check `botSource != null`
2. Get server via `botSource.getServer()`
3. Execute command via `server.getCommandManager().executeWithPrefix(botSource, command)`

**Example template**:
```java
public static void methodName(params...) {
    LOGGER.info("Calling method: methodName with params...");
    
    if (botSource == null) {
        LOGGER.error("botSource is null");
        return;
    }
    
    MinecraftServer server = botSource.getServer();
    if (server == null) {
        LOGGER.error("server is null");
        return;
    }
    
    String command = "/player " + botSource.getName() + " action params...";
    server.getCommandManager().executeWithPrefix(botSource, command);
    
    LOGGER.info("✓ Command executed");
}
```

### Fix #3: Parameter Generation for Markov Plans
**File**: `FunctionCallerV2.java`, `convertStepToParams()` method

**Current issue**: Default params may use bucket coordinates instead of actual targets

**Better approach**:
```java
case "mineBlock":
    // Instead of using bucket coords, use BlockDetection
    if (paramArray.length >= 4) {
        // Use provided coords
        params.put("targetX", paramArray[0]);
        params.put("targetY", paramArray[1]);
        params.put("targetZ", paramArray[2]);
    } else {
        // Find nearest mineable block
        BlockPos nearest = BlockDetection.findNearestBlock(
            botSource.getPlayer(),
            "minecraft:oak_log",
            32 // search radius
        );
        
        if (nearest != null) {
            params.put("targetX", String.valueOf(nearest.getX()));
            params.put("targetY", String.valueOf(nearest.getY()));
            params.put("targetZ", String.valueOf(nearest.getZ()));
        }
    }
    break;
```

### Fix #4: Enable Markov Learning
**File**: `MarkovChain2.java` and `ActionLogWriter.java`

**Add after each successful step**:
```java
// In executePlan() after step completion:
if (success && logWriter != null) {
    // Update Markov chain with successful transition
    markovChain.observeTransition(
        plan.goalId,
        initialState.getContextHash(),
        prevPrevAction,
        prevAction,
        currentAction
    );
}
```

---

## 🧪 TESTING PROTOCOL

### Test 1: Single Action Execution
```bash
# Test basic movement
/bot goal Move forward 10 blocks

# Expected: Bot physically moves forward in-game
```

### Test 2: Simple Mining Task
```bash
# Spawn bot near oak trees
/bot goal Mine some oak logs

# Expected:
# 1. Bot detects nearby oak logs
# 2. Bot navigates to nearest log
# 3. Bot mines the log
# 4. Oak log appears in bot's inventory
```

### Test 3: Building Task
```bash
# Give bot cobblestone
/give @p minecraft:cobblestone 64

/bot goal Build a small wall

# Expected:
# 1. Bot places cobblestone blocks
# 2. Blocks physically appear in world
```

### Test 4: Complex Multi-Step Task
```bash
/bot goal Gather wood and build a crafting table

# Expected plan:
# 1. detectBlocks oak_log
# 2. goTo nearest_log_coords
# 3. mineBlock oak_log_coords (repeat x4)
# 4. craft planks
# 5. craft crafting_table
# 6. placeBlock crafting_table
```

---

## 📊 SUCCESS METRICS

| Metric | Current | Target |
|--------|---------|--------|
| Goal parsing time | <1ms | ✅ |
| Plan generation time | 30-40ms | ✅ |
| Plan quality (risk score) | 60-80 | ✅ |
| In-game execution | ❌ 0% | 100% |
| Markov learning | ❌ Not active | Active |

---

## 🚀 DEPLOYMENT CHECKLIST

Before declaring "complete":

- [ ] Add debug logs to all Tools.* methods
- [ ] Verify botSource is never null during execution
- [ ] Test each tool individually (/bot call toolName ...)
- [ ] Verify bot physically moves/mines/builds in-game
- [ ] Enable ActionLogWriter to update Markov chain
- [ ] Test with 10 different goal types
- [ ] Verify plans improve over time (learning works)
- [ ] Document known limitations
- [ ] Add user-facing documentation

---

## 📝 KNOWN LIMITATIONS

### Current Limitations:
1. **No parameter learning**: Markov chain learns action sequences but not optimal parameter values
2. **No failure recovery**: If a step fails, plan continues (should re-plan or abort)
3. **No state verification**: Doesn't check if expected state change occurred
4. **No dynamic re-planning**: Can't adapt to unexpected obstacles
5. **No multi-goal planning**: Can't chain multiple goals (e.g., "get wood then build house")

### Future Enhancements:
- **Hierarchical planning**: Break complex goals into sub-goals
- **Parameter embeddings**: Learn optimal parameter ranges via RL
- **State prediction**: Use CheapForward for better lookahead
- **Failure detection**: Monitor state changes and re-plan on failure
- **Multi-step goals**: Support goal sequences and dependencies

---

## 🔍 IF STILL BROKEN AFTER FIXES

### Nuclear Option: Bypass Tools.java Entirely

Modify `callFunction()` to send commands directly:

```java
case "goTo" -> {
    int x = Integer.parseInt(resolvePlaceholder(paramMap.get("x")));
    int y = Integer.parseInt(resolvePlaceholder(paramMap.get("y")));
    int z = Integer.parseInt(resolvePlaceholder(paramMap.get("z")));
    
    // BYPASS Tools.goTo() - send command directly
    String command = String.format("/player %s goto %d %d %d", 
                                   botSource.getName(), x, y, z);
    logger.info("🔧 Direct command: {}", command);
    botSource.getServer().getCommandManager().executeWithPrefix(botSource, command);
}
```

If this works, problem is in Tools.java.  
If this doesn't work, problem is in botSource/command system.

---

## ✅ FINAL CHECKLIST

When you can run `/bot goal Fetch some wood please` and the bot:
- ✅ Detects nearby oak logs
- ✅ Navigates to nearest log
- ✅ Mines the log
- ✅ Wood appears in inventory
- ✅ Plan completes successfully

Then the Markov planner is **COMPLETE**.

---

**Last Updated**: December 12, 2025  
**Author**: AI Assistant  
**Next Review**: After Tools.java verification

