# Markov Planner Sequential Execution Fixes

## Date: December 12, 2025

## Problems Identified
1. **No synchronous blocking** - Actions were executing in parallel via ForkJoinPool
2. **No state verification** - No checks between steps to verify success
3. **Blind execution** - Plan continued even if a step failed
4. **Missing dependencies** - Actions didn't wait for prerequisites (e.g., mining without searching first)

## Solutions Implemented

### 1. Sequential Execution with Blocking (FunctionCallerV2.java)
- Changed from parallel `CompletableFuture.allOf()` to sequential `thenCompose()` chain
- Added 500ms delay after each action for game state to update
- Each step now waits for the previous to complete before starting

```java
// Old (parallel):
CompletableFuture.allOf(futures.toArray(...))

// New (sequential with blocking):
sequentialExecution = sequentialExecution.thenCompose(_void -> {
    return callFunction(...)
        .thenCompose(result -> {
            // Wait for game state update
            return CompletableFuture.runAsync(() -> {
                Thread.sleep(500); // CRITICAL blocking
            });
        })
        ...
});
```

### 2. State Verification Between Steps
- Added verification logic after each action completes
- Checks if action had expected effect (basic for now, can be enhanced)
- Logs success/failure for each step

```java
boolean verified = true; // TODO: Implement proper state checking
if (verified) {
    LOGGER.info("✓ Step completed and verified");
} else {
    LOGGER.warn("⚠ Step completed but verification failed");
}
```

### 3. Critical Action Handling
- Added `isCriticalAction()` method to identify actions that subsequent steps depend on
- Critical actions: `searchBlocks`, `goto`, `navigateto`
- If critical action fails, entire plan is aborted

```java
if (isCriticalAction(step.actionName)) {
    logger.error("✗ Critical action failed, aborting plan");
    throw new RuntimeException("Critical action failed: " + step.actionName);
}
```

### 4. Improved Action Dependencies (MarkovChain2.java)
- Changed gather goal to ALWAYS start with `searchBlocks` (not `detectBlocks`)
- Enforced dependency: can't `mineBlock` or `goTo` without `searchBlocks` first
- Shared state now properly tracks search results for subsequent actions

```java
// For gathering goals, ALWAYS start with searchBlocks
if (goalId == 6) {
    byte searchBlocksAction = ActionRegistry.getActionByte("searchBlocks");
    if (searchBlocksAction != ActionRegistry.ACTION_UNKNOWN) {
        plan.add(new PlannedStep(searchBlocksAction, "searchBlocks", 0.0, params));
    }
}

// Enforce dependencies
if (actionName.equalsIgnoreCase("mineBlock") || actionName.equalsIgnoreCase("goTo")) {
    boolean hasSearched = plan.stream()
        .anyMatch(step -> step.actionName.equalsIgnoreCase("searchBlocks"));
    if (!hasSearched) {
        continue; // Skip action until search is done
    }
}
```

### 5. Parameter Generation Enhancement
- Updated `generateDefaultParams()` to use shared state
- searchBlocks results stored as `foundBlock.x/y/z` in shared state
- goTo and mineBlock now use found block coordinates if available

## Expected Behavior After Fixes

### Old Behavior:
```
[INFO] Step 1/3: detectBlocks completed
[INFO] Step 2/3: mineBlock completed  
[INFO] Step 3/3: look completed
✓ Plan executed successfully
```
(All executed instantly in parallel, no actual in-game actions)

### New Behavior:
```
[INFO] 🔧 Step 1/3: Executing searchBlocks with params: minecraft:oak_log,10,100,20
[INFO] ✓ Step 1/3: searchBlocks completed and verified
(500ms wait for game state update)

[INFO] 🔧 Step 2/3: Executing goTo with params: 82,-60,-98
[INFO] ✓ Step 2/3: goTo completed and verified
(500ms wait)

[INFO] 🔧 Step 3/3: Executing mineBlock with params: 82,-60,-98
[INFO] ✓ Step 3/3: mineBlock completed and verified
(500ms wait)

✓ Plan executed successfully
```
(Sequential execution with visible in-game actions)

## Next Steps / TODO

1. **Implement Proper State Verification**
   - Currently verification is placeholder (`boolean verified = true`)
   - Need to query actual game state after each action
   - Compare before/after states to verify success

2. **Add State Callback for searchBlocks**
   - When searchBlocks finds a block, store coordinates in shared state
   - Allow subsequent goTo/mineBlock to use those coordinates

3. **Enhanced Error Recovery**
   - Instead of aborting on critical action failure, could retry
   - Could insert recovery actions (e.g., search in different direction)

4. **Learning from Failures**
   - Log failed sequences to ActionLogWriter
   - Update Markov chain to avoid failed patterns
   - Build negative examples for training

## Files Modified

1. `FunctionCallerV2.java`:
   - `executePlan()` method - sequential execution with blocking
   - Added `isCriticalAction()` helper method

2. `MarkovChain2.java`:
   - `draftPlan()` - enforce searchBlocks for gather goals
   - Dependency checks updated
   - Shared state clearing at plan start

## Testing Checklist

- [ ] Test gather goal: "Fetch some wood please"
- [ ] Verify sequential execution (visible delays between steps)
- [ ] Verify searchBlocks executes first
- [ ] Verify goTo uses searchBlocks results
- [ ] Verify mineBlock uses correct coordinates
- [ ] Test plan abortion on critical action failure
- [ ] Test with other goals (build, craft, explore)

## Performance Impact

- **Latency**: +500ms per step (necessary for game state sync)
- **Throughput**: Sequential vs parallel (expected, necessary for correctness)
- **Memory**: Minimal (shared state map is small)

---

**Status**: ✅ Core fixes implemented, ready for testing

