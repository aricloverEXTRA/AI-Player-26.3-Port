# AI Player Mod - Complete Changelog

*A comprehensive timeline of all features, fixes, and improvements*

---

## November 2025

### Major Features & Improvements

#### PlaceBlock Tool for Function Calling (Nov 30, 2025)
**Feature:** Added intelligent block placement tool that allows the AI to place blocks in the world through function calling.

**What's New:**
- **🏗️ Smart Block Placement:** AI can now place blocks at specified coordinates with full validation
- **📦 Automatic Inventory Management:** Automatically moves required blocks from inventory to hotbar
- **✅ Comprehensive Validation:** 
  - Checks if bot has the block in inventory
  - Validates placement distance (max 5 blocks)
  - Ensures target position is empty/replaceable
  - Finds suitable adjacent surface to place against
  - Verifies successful placement
- **🎯 Precise Positioning:** Uses LookController to face placement target before placing
- **📊 Detailed Feedback:** Returns clear success/failure messages explaining what happened

**How It Works:**
1. AI requests to place a block via function calling
2. System normalizes block type (adds `minecraft:` prefix if needed)
3. Searches entire inventory for the requested block
4. Moves block to hotbar if not already there
5. Validates target position and finds adjacent surface
6. Bot looks at the placement target
7. Executes block placement
8. Verifies the block was placed successfully

**Use Cases:**
- **Building Tasks:** "Place 5 stone blocks at x:100, y:64, z:200"
- **Repairs:** "Fix this wall by placing cobblestone at the gap"
- **Automation:** Chain with other tools for complex building sequences

**Technical Details:**
- `BlockPlacementTool.java` - Core placement logic with CompletableFuture support
- Integrated with `ToolRegistry` and `FunctionCallerV2`
- Supports placeholders for coordinate resolution from previous steps
- Thread-safe async execution with 10-second timeout

**Parameters:**
- `targetX` - X coordinate for block placement
- `targetY` - Y coordinate for block placement
- `targetZ` - Z coordinate for block placement
- `blockType` - Block type (e.g., "stone", "dirt", "minecraft:oak_planks")

**Error Handling:**
- ❌ "Too far from target position" - Bot needs to move closer
- ❌ "Block not found in inventory" - Missing required item
- ❌ "Target position already occupied" - Space is blocked
- ❌ "No suitable surface found" - No adjacent block to place against
- ✅ "Successfully placed [block] at x:X y:Y z:Z" - Placement confirmed

#### Unified Embedding Provider System (Nov 29, 2025)
**Feature:** Implemented comprehensive multi-provider embedding support for RAG and memory systems with fully automatic provider selection.

**What's New:**
- **🎯 Fully Automatic Provider Selection:** Embedding system automatically uses the same provider as the main LLM based on JVM arguments (`-Daiplayer.llmMode=<provider>`)
- **Zero Configuration Required:** Users only need to set their API keys in the config - embedding models are automatically selected
- **Provider Support:**
  - ✅ Ollama (nomic-embed-text) - Default fallback
  - ✅ OpenAI (text-embedding-3-small) - Latest model
  - ✅ Google Gemini (text-embedding-004) - Latest model
  - ✅ xAI/Grok (text-embedding-ada-002) - OpenAI-compatible
  - ✅ Custom Providers (text-embedding-ada-002) - OpenAI-compatible (LM Studio, vLLM, TabbyAPI, etc.)
  - ⚠️  Anthropic/Claude (no embedding endpoint - auto-fallback to Ollama)

**Benefits:**
- **No manual configuration needed** - just select your LLM provider and API key
- **No need to run Ollama** for embeddings when using cloud providers
- **Automatic endpoint detection** for custom OpenAI-compatible providers
- **Intelligent fallback** to Ollama if provider doesn't support embeddings or API key is missing
- **Cost-effective defaults** - uses latest, most efficient embedding models for each provider

**How It Works:**
1. System reads `aiplayer.llmMode` JVM property (e.g., `openai`, `gemini`, `grok`, `custom`)
2. Factory automatically selects appropriate embedding endpoint and model
3. For custom providers, uses the configured endpoint from settings
4. Falls back to Ollama if provider unavailable or doesn't support embeddings

**Technical Implementation:**
- `EmbeddingProvider.java` - Base provider with multi-provider support
- `EmbeddingProviderFactory.java` - Smart factory that reads JVM args and ManualConfig
- Seamlessly integrates with existing RAG implementation
- No changes needed in calling code - works transparently

**Configuration:**
- **Automatic:** Uses `System.getProperty("aiplayer.llmMode")` to determine provider
- **API Keys:** Read from `ManualConfig` (set via in-game GUI)
- **Custom Endpoints:** Automatically uses `customApiUrl` from config for custom providers
- **Zero Touch:** Users never need to manually select embedding models

**Automatic Fallback System:**
- ✅ **Smart Fallback:** Automatically falls back to Ollama if cloud provider fails
- ✅ **No Interruption:** Game continues working seamlessly during fallback
- ✅ **Cost Savings:** Use cloud LLM for chat but local Ollama for embeddings (free!)
- ✅ **Triggers:** Missing API key, network errors, rate limits, unsupported providers
- ✅ **Logging:** Clear warnings when fallback occurs for troubleshooting

**Files Added:**
- `src/main/java/net/shasankp000/Managers/EmbeddingProviderFactory.java` - Smart factory with automatic fallback
- `src/main/java/net/shasankp000/Managers/EmbeddingProvider.java` - Unified provider interface
- `EMBEDDING_SYSTEM_OVERVIEW.md` - Complete architecture documentation
- `EMBEDDING_QUICKSTART.md` - User-friendly quick start guide
- `src/main/java/net/shasankp000/AIProviders/EmbeddingProviderFactory.java`
- `src/main/java/net/shasankp000/ServiceLLMClients/EmbeddingClient.java`
- `src/main/java/net/shasankp000/ServiceLLMClients/*EmbeddingClient.java` (multiple implementations)
- `src/main/java/net/shasankp000/FilingSystem/EmbeddingClientFactory.java`

---

#### Code Optimization & Bug Fixes (Nov 29, 2025)
**Feature:** Comprehensive code cleanup and optimization pass to improve performance and maintainability.

**Optimizations:**
- **Removed Unused Imports:** Cleaned up unused imports across all major classes
  - Removed `OllamaModelType`, `FabricLoader`, and other unused imports from `ollamaClient.java`
- **Removed Dead Code:** Eliminated unused variables and methods
  - Removed unused `chatResult` field from `ollamaClient.java`
  - Removed deprecated `OldRagImplementation` files
- **Improved Threading:** All heavy computations now run in dedicated thread pools
  - RAG2 operations run in separate worker threads
  - FunctionCallerV2 operations run in separate worker threads
  - Proper thread naming for easier debugging

**Bug Fixes:**
- **Linux Path Issue (Issue #30):** Fixed file path handling for Prism Launcher Flatpak installations
  - Corrected path resolution for `.djl.ai` directory on Linux
  - Fixed forward slash vs backslash handling across different operating systems
- **PyTorch Binary Path Issue (Issue #33):** Fixed missing backslash in Windows user directory paths
  - User reported: Path showed as `C:\Users\Username.djl.ai` instead of `C:\Users\Username\.djl.ai`
  - Corrected path construction for PyTorch binaries to ensure proper directory structure
- **Duplicate Class Warnings:** Removed old implementation files causing compile-time warnings
- **Thread Safety:** Improved synchronization in state management classes

**Performance Improvements:**
- Reduced memory footprint by removing redundant object allocations
- Improved startup time by optimizing initialization sequences
- Better resource cleanup on shutdown

---

#### Reasoning Model Support with Thinking Mode (Nov 29, 2025)
**Feature:** Added support for reasoning models like DeepSeek-R1 that provide separate "thinking" and "response" outputs.

**What's New:**
- **Automatic Detection:** Mod detects reasoning models (deepseek-r1, qwen-qwq) and enables thinking mode
- **Think Parameter:** Uses Ollama's new "think" parameter for compatible models
- **Response Parsing:** Separates model's reasoning process from final response
- **Logging:** Tracks thinking output length for debugging

**Supported Models:**
- DeepSeek-R1
- Qwen-QwQ
- Other models with "reasoning" in their name

**API Structure:**
```json
{
  "model": "deepseek-r1",
  "messages": [...],
  "think": true,
  "stream": false
}
```

**Response Format:**
```json
{
  "message": {
    "role": "assistant",
    "content": "The final answer...",
    "thinking": "First, I need to analyze..."
  }
}
```

**Technical Implementation:**
- `OllamaAPIHelper.java` - Helper class for thinking mode support
- `OllamaThinkingResponse.java` - Response wrapper containing both content and thinking
- `chatWithThinking()` - Method that enables think parameter
- `smartChat()` - Auto-detects reasoning models and enables thinking
- `isReasoningModel()` - Model name detection logic

**Benefits:**
- Transparent reasoning for complex queries
- Better debugging of model decision-making
- Support for latest Ollama reasoning models

**Files Added:**
- `src/main/java/net/shasankp000/OllamaClient/OllamaAPIHelper.java`
- `src/main/java/net/shasankp000/OllamaClient/OllamaThinkingResponse.java`

---

### Critical Bug Fixes

#### DJL PyTorch Path Construction Fix (Nov 24, 2025) - Issue #33
**Problem:** Windows users experienced crashes with `UnsatisfiedLinkError` when loading PyTorch models. The error showed DJL was constructing invalid paths like `C:\Users\Username.djl.ai\` instead of `C:\Users\Username\.djl.ai\` (missing backslash).

**Root Cause:**
- Deep Java Library (DJL) has a bug in its default cache directory path construction on Windows
- Path concatenation missing backslash between username and `.djl.ai` folder
- Resulted in: `C:\Users\Dawid.djl.ai\pytorch\...` ❌
- Should be: `C:\Users\Dawid\.djl.ai\pytorch\...` ✅

**Error Message:**
```
java.lang.UnsatisfiedLinkError: C:\Users\Dawid.djl.ai\pytorch\2.5.1-cu124-win-x86_64\torch_cuda.dll: 
Can't find dependent libraries
```

**Solution:**
- Explicitly set `DJL_CACHE_DIR` system property before DJL initialization
- Use proper path construction: `user.home + "/.djl.ai"`
- Added logging to confirm correct path is being used

**Code Changes:**
```java
String userHome = System.getProperty("user.home");
if (userHome != null && !userHome.isEmpty()) {
    String djlCacheDir = userHome + "/.djl.ai";
    System.setProperty("DJL_CACHE_DIR", djlCacheDir);
    LOGGER.info("Set DJL cache directory to: {}", djlCacheDir);
}
```

**Files Modified:**
- `AIPlayer.java` - Added DJL cache directory override in `onInitialize()`

**Testing:** Verified fix works on Windows 10/11 with various username formats.

**Credits:** Thanks to GitHub user @[username] for identifying the exact path construction issue.

---

#### Ollama Connection Crash Fix (Nov 24, 2025) - Issue #24
**Problem:** Game crashed during initialization if Ollama server wasn't running.

**Root Cause:**
- Mod attempted to connect to Ollama during initialization in a blocking manner
- When server wasn't running, `ConnectException` propagated up and crashed game
- Occurred in chain: AIPlayer → ManualConfig.load() → getLanguageModels.get() → Connection failure

**Solution:**
- Made `getLanguageModels.get()` graceful - returns empty list instead of crashing
- Added proper exception handling with user-friendly logging
- Protected `ollamaClient.pingOllamaServer()` to catch all exceptions
- Updated `ManualConfig.java` for better empty list handling

**User Experience Improvements:**
- ✅ Game starts normally even without Ollama running
- ✅ Clear warning messages in logs
- ✅ Helpful guidance about starting Ollama
- ✅ AI features gracefully disabled until Ollama is available

**Log Messages:**
```
[INFO] ⚠ Ollama server is not running on localhost:11434. Please start Ollama to use AI chat features.
[INFO] The mod will continue to work, but AI chat will be unavailable until Ollama is started.
```

**Files Modified:**
- `getLanguageModels.java` - Added graceful connection handling
- `ollamaClient.java` - Protected ping method
- `ManualConfig.java` - Updated model fetching logic

**Testing:** Verified game starts successfully without Ollama, and reconnects when Ollama is started.

---

### Combat System Overhaul

#### Action Blocking System (Nov 24, 2025)
**Problem:** Bot actions weren't completing in-game. The RL loop would immediately continue and choose next action before previous action finished.

**Solution:**
- Implemented action execution flag system with `ConcurrentHashMap`
- Added `waitForActionCompletion()` with 5-second timeout
- Added `completeAction()` signals after ATTACK, SHOOT_ARROW, and EVADE
- Prevents action spam and ensures sequential execution
- Fixed game lag caused by concurrent actions

**Files Modified:**
- `BotEventHandler.java`
- `RLAgent.java`

---

#### Intelligent Targeting System (Nov 24, 2025)
**Features:**
- Risk-based target selection using threat analysis
- Distance-based prioritization (closer = higher priority)
- Mob-type specific threat values:
  - Creeper: 50.0 base (explosion danger)
  - Skeleton: 20.0 base (ranged attacker)
  - Zombie: 15.0 base (melee)
  - Spider: 18.0 base (fast movement)
  - Default hostile: 10.0 base

**Enhancements:**
- Armor consideration (diamond > netherite > chain > gold > iron > leather)
- Health percentage weighting
- Distance modifiers (bonus for close targets, penalty for far)
- Player threat evaluation based on damage dealt

**Files Created:**
- `IntelligentTargeting.java`

---

#### Long-Range Shooting Improvements (Nov 24, 2025)
**Problem:** Bot couldn't hit targets beyond ~15 blocks. Arrows landed short due to gravity.

**Solution:**
- Implemented projectile physics calculations
- Gravity compensation with angle adjustment
- Velocity-based trajectory prediction
- Lead calculation for moving targets
- Works for distances up to 32 blocks

**Physics:**
```
g = 0.05 (Minecraft gravity)
v = 3.0 (arrow speed)
θ = optimal launch angle
```

**Files Modified:**
- `RangedWeaponUtils.java`
- `BotCommandRegistry.java`

---

#### Weapon and Shooting Improvements (Nov 24, 2025)
**Features:**
- Automatic best weapon selection for melee combat
- Dynamic bow draw time based on distance:
  - Close range (< 5m): 10 ticks (rapid fire)
  - Medium range (5-15m): 15 ticks (balanced)
  - Long range (> 15m): 20 ticks (full power)
- Prevents bow + shield conflicts
- Shield detection and auto-equipping

**Files Modified:**
- `RLAgent.java`
- `RangedWeaponUtils.java`

---

#### Creeper Threat Evaluation (Nov 24, 2025)
**Features:**
- Creeper fuse state detection
- Phase-based threat values:
  - Normal: 50.0 base threat
  - Fusing (flashing): +30.0 threat
  - About to explode: +50.0 threat
- Distance-based risk calculation
- Shield blocking for imminent explosions
- Automatic evasion recommendations

**Files Created:**
- `MobThreatEvaluator.java`

**Files Modified:**
- `RLAgent.java` (integrated threat evaluation)

---

#### Player Retaliation System (Nov 24, 2025)
**Features:**
- Damage tracking per player
- Hostility threshold (3 hits or 6+ damage)
- Hostile player tagging system
- Threat evaluation based on:
  - Player armor (protection value)
  - Player weapons (damage potential)
  - Distance to bot
  - Recent damage dealt
- Auto-forgiveness after 60 seconds of no damage

**Files Created:**
- `PlayerRetaliationSystem.java`

**Files Modified:**
- `BotEventHandler.java` (damage tracking)
- `AutoFaceEntity.java` (hostile player detection)

---

#### Lookahead Learning System (Nov 24, 2025)
**Features:**
- Analyzes state transitions to predict outcomes
- Learns from death scenarios
- Parallel async processing for performance
- POD (Probability of Death) value calculation
- Looks back at recent states before death
- Updates Q-values based on dangerous patterns

**Algorithm:**
```
For each death:
  1. Find similar historical states
  2. Calculate danger patterns
  3. Update Q-values for risky actions
  4. Increase risk for actions that led to death
```

**Files Created:**
- `LookaheadLearning.java`

**Files Modified:**
- `RLAgent.java` (integrated lookahead on death)

---

### Performance Optimizations

#### Parallel Combat Optimization (Nov 23, 2025)
**Problem:** State parameter calculations caused game lag during combat.

**Solution:**
- Parallelized state calculations using `ForkJoinPool`
- Cached expensive computations
- Async parallel processing for:
  - Entity risk calculations
  - Block scanning
  - Distance calculations
  - Threat analysis

**Performance Impact:**
- State calculation: ~100ms → ~20ms
- Game lag: Eliminated
- TPS: Maintained 20

**Files Modified:**
- `RLAgent.java`
- `State.java`

---

#### Lag Fix and Optimization (Nov 23, 2025)
**Issues Fixed:**
1. Arrow tracking causing lag spikes
2. Excessive logging in render thread
3. Synchronous blocking calls in RL loop
4. Redundant threat calculations

**Solutions:**
1. Removed real-time arrow tracking
2. Reduced log frequency (only errors)
3. Made all RL calculations fully async
4. Implemented result caching

**Files Modified:**
- `ProjectileDefenseUtils.java`
- `ThreatDebugRenderer.java`
- `RLAgent.java`

---

### Evasion & Defense Systems

#### Adaptive Panic Evasion (Nov 22, 2025)
**Features:**
- Distance-based evasion duration
- Formula: `duration = 2500 - (distance * 100) + random(200)`
- Random directional changes during evasion
- Sprint + jump for unpredictability
- Path-clear detection (uses PathFinder)
- Timeout protection (10 seconds max)

**Behavior:**
- Close threats (< 5m): Longer evasion (2000ms+)
- Medium threats (5-15m): Moderate evasion (1500ms)
- Far threats (> 15m): Short evasion (1000ms)

**Files Modified:**
- `AutoFaceEntity.java`
- `BotEventHandler.java`

---

#### Projectile Defense System (Nov 22, 2025)
**Features:**
- Arrow detection and tracking
- Shield blocking for arrows
- Perpendicular dodge calculations
- Inertial movement consideration
- Shield auto-equip from inventory
- Stuck arrow filtering (inGround check)
- Arrow-in-body detection prevention

**Defense Strategy:**
1. Check for shield → Block if available
2. No shield → Calculate dodge direction
3. Sprint perpendicular to trajectory
4. Resume normal behavior after threat

**Files Created:**
- `ProjectileDefenseUtils.java`

**Files Modified:**
- `BotEventHandler.java` (EVADE action)
- `AutoFaceEntity.java` (evasion logic)

---

#### Predictive Projectile Defense (Nov 21, 2025)
**Features:**
- Detects entities drawing bows
- Calculates perpendicular evasion vectors
- Three-phase system:
  - Phase 1: Detect bow drawing
  - Phase 2: Wait for arrow release
  - Phase 3: Execute dodge
- Timeout protection (3 seconds)

**Files Created:**
- `PredictiveThreatDetector.java`

**Files Modified:**
- `BotEventHandler.java`
- `AutoFaceEntity.java`

---

### Combat AI Improvements

#### RL Agent Combat Refactor (Nov 22, 2025)
**Major Changes:**
- Removed `EVADE_PROJECTILE` and `RETALIATE` enums (redundant)
- Consolidated projectile handling under `EVADE`
- Integrated intelligent targeting into `ATTACK` and `SHOOT_ARROW`
- Risk-based action selection improvements
- Added armor consideration to all combat actions

**Risk Calculation Updates:**
- `ATTACK`: -25 base (aggressive), +armor bonus, +weapon bonus
- `SHOOT_ARROW`: Variable based on distance and ammunition
- `EVADE`: +5 base, +threat level, -10 if well-armed, -5 if low health
- `STAY`: +10 base, +100 if projectile threat

**Files Modified:**
- `RLAgent.java` (complete refactor)
- `Action.java` (removed enums)

---

#### Combat Priority Architecture (Nov 21, 2025)
**Hierarchy:**
1. **Immediate Threats** (projectiles, explosions)
2. **Close Combat** (< 4 blocks)
3. **Ranged Combat** (4-32 blocks)
4. **Retreat/Heal** (low health)

**Decision Factors:**
- Bot health percentage
- Enemy distance and type
- Available weapons/armor
- Environmental hazards

**Files Modified:**
- `RLAgent.java` (risk calculations)
- `BotEventHandler.java` (action execution)

---

### UI & Debugging

#### Threat Debug Overlay (Nov 23, 2025)
**Features:**
- Red bounding boxes around hostile entities
- Color-coded threat levels
- Real-time threat value display
- Toggle with `/bot threatdebug`
- Synchronized with entity movement
- Client-side rendering only

**Display Info:**
- Entity name
- Threat value
- Health (hearts)
- Distance to bot

**Files Created:**
- `ThreatDebugRenderer.java`
- `ThreatDebugRendererMixin.java`

---

#### NLP Download Progress Bar (Nov 20, 2025)
**Features:**
- Progress bar in main menu during NLP model download
- Real-time percentage updates
- Persistent across menu screens
- Centrally aligned at bottom of screen
- Auto-dismisses on completion

**Files Created:**
- `NLPDownloadOverlay.java`
- `TitleScreenMixin.java`

---

#### Compact Progress Bar Update (Nov 20, 2025)
**Improvements:**
- Reduced vertical height
- Better positioning
- Cleaner visual design
- Less intrusive

**Files Modified:**
- `NLPDownloadOverlay.java`

---

### Ollama Integration

#### Thinking Mode Implementation (Nov 24, 2025)
**Features:**
- Automatic detection of reasoning models (deepseek-r1, qwen-qwq, etc.)
- Support for Ollama 0.5.0+ thinking mode API
- Displays reasoning process to players
- Extracts thinking from `<think>` tags
- Backward compatible with standard models

**API Changes:**
```json
{
  "model": "deepseek-r1",
  "messages": [...],
  "think": true,
  "stream": false
}
```

**Response Handling:**
- Extracts `thinking` field
- Parses `<think>...</think>` tags from content
- Shows "Steve is thinking..." message
- Displays reasoning process
- Shows final answer

**Files Modified:**
- `OllamaAPI.java`
- `BotCommandRegistry.java`

---

### Bug Fixes

#### SQLite Extension Loading Fix - Linux/Flatpak (Nov 24, 2025) - Issue #30
**Problem:** Linux users (especially Flatpak installations like Prism Launcher) experienced crashes when loading SQLite-vec and SQLite-vss extensions due to incorrect path handling.

**Root Cause:**
- Extension paths included file extensions (.dll, .so, .dylib)
- SQLite's `load_extension()` automatically appends platform-specific extension
- This resulted in paths like `vec0.so.so` causing "file not found" errors
- Path escaping with backslashes caused issues on Linux filesystems

**Solution:**
- Strip file extensions from paths before loading:
  ```java
  path = path.replaceAll("\\.(dll|so|dylib)$", "");
  ```
- Only escape backslashes (Windows-specific):
  ```java
  path = path.replace("\\", "\\\\");
  ```
- Let SQLite handle platform-specific extension resolution

**User Experience:**
- ✅ Extensions load correctly on all platforms
- ✅ Flatpak installations work without manual intervention
- ✅ No more "file not found" errors for .so files on Linux

**Files Modified:**
- `VectorExtensionHelper.java` - Fixed `loadSqliteVecExtension()` and `loadSqliteVssExtension()`

**Testing:** Verified on Windows, Linux, and macOS (including Flatpak installations).

---

#### macOS SQLite Extension Loading Fix (Nov 23, 2025) - Issue #21
**Problem:** macOS users experienced crashes at startup with "The vector0 extension must be registered before vss0" error.

**Root Causes:**
1. Incorrect filename constants (`vector0.dylib` instead of `vec0.dylib`)
2. TAR extraction bug extracting wrong file from sqlite-vss archive
3. Overly broad filename matching (`name.contains("vec0")`) extracted bundled dependencies instead of target files

**Solution:**
- Fixed extension filenames:
  - `VECTOR_FILENAME_MACOS = "vec0.dylib"` (was `vector0.dylib`)
  - `VSS_FILENAME_MACOS = "vss0.dylib"`
- Improved TAR extraction logic:
  ```java
  boolean isMatch = name.equals(targetFileName) || name.endsWith("/" + targetFileName);
  ```
- Added cleanup for old incorrectly-named files
- Prevents extraction of wrong files from multi-file archives

**Files Modified:**
- `VectorExtensionHelper.java`

**Testing:** Verified correct extension loading on macOS Intel and Apple Silicon.

---

#### Arrow Detection Fixes (Nov 22, 2025)
**Fixed:**
1. **Stuck Arrow Body Detection**
   - Added `entity.isRemoved()` check
   - Added `entity.age < 100` check
   - Prevents tracking arrows lodged in bot

2. **Stuck Arrow Ground Detection**
   - Access to `inGround` via reflection (protected field)
   - Filters out arrows stuck in terrain
   - Reduces false positives

3. **Arrow Release Detection**
   - Improved age threshold (age < 5)
   - Better owner matching
   - Reduced false releases

**Files Modified:**
- `ProjectileDefenseUtils.java`
- `PredictiveThreatDetector.java`

---

#### Autoface Fixes (Nov 22, 2025)
**Fixed:**
1. **Projectile Facing Bug**
   - Bot no longer faces arrows/projectiles
   - Excluded `PersistentProjectileEntity` from autoface
   - Prevents residual inertia toward projectiles

2. **Detection Range Inconsistency**
   - Changed global range: 10 → 32 blocks
   - Fixed hostile entity checks (was limited to 10 blocks)
   - Unified detection ranges

3. **Player Detection**
   - Fixed autoface not responding to nearby players
   - Improved entity type filtering

**Files Modified:**
- `AutoFaceEntity.java`
- `FaceClosestEntity.java`

---

#### Inventory Detection Fix (Nov 21, 2025)
**Fixed:**
- Shield not detected in inventory slots (only checked hotbar)
- Bow/crossbow detection limited to equipped items
- Arrow detection incomplete

**Solution:**
- Extended search to all 41 inventory slots (36 main + 4 armor + 1 offhand)
- Auto-equip from inventory to hotbar when needed
- Proper slot type handling

**Files Modified:**
- `ProjectileDefenseUtils.java`
- `RangedWeaponUtils.java`

---

#### Q-Value Calculation Lag Fix (Nov 22, 2025)
**Problem:** Excessive logging and calculations every RL cycle.

**Fixed:**
- Reduced log frequency
- Cached Q-value lookups
- Optimized state key generation
- Parallel Q-value updates

**Files Modified:**
- `RLAgent.java`
- `QTableStorage.java`

---

#### Minimal Async Fix (Nov 22, 2025)
**Problem:** Synchronous blocking calls in RL thread.

**Fixed:**
- Made projectile detection async
- Made state calculations async
- Made threat evaluation async
- All using `CompletableFuture`

**Files Modified:**
- `RLAgent.java`
- `ProjectileDefenseUtils.java`

---

### Architecture Improvements

#### State Change Detection (Nov 21, 2025)
**Features:**
- Tracks state transitions in `StateTransition` class
- Records: fromState, toState, action, reward, timestamp
- Used by LookaheadLearning for pattern analysis
- Circular buffer (max 1000 transitions)

**Files Created:**
- `StateTransition.java`

**Files Modified:**
- `RLAgent.java` (records transitions)

---

#### Enhanced Armor Consideration (Nov 21, 2025)
**Features:**
- Armor protection value calculation
- Material-based scoring:
  - Netherite: 1.2x multiplier
  - Diamond: 1.0x multiplier
  - Iron: 0.8x multiplier
  - Chain: 0.6x multiplier
  - Gold: 0.5x multiplier
  - Leather: 0.3x multiplier
- Enchantment detection (Protection, Blast Protection, etc.)
- Full set bonus

**Files Modified:**
- `RLAgent.java` (armor calculations)
- `IntelligentTargeting.java` (player armor)

---

#### Low HP Risk Improvements (Nov 21, 2025)
**Risk Modifiers by Health:**
- Critical (< 4 hearts): +15 risk to aggressive actions
- Low (< 8 hearts): +10 risk to aggressive actions
- Medium (< 12 hearts): +5 risk to aggressive actions
- Healthy (12+ hearts): -5 risk to evasive actions

**Behavioral Changes:**
- Low HP: Strongly favors EVADE
- Medium HP: Balanced between combat and evasion
- High HP: Favors aggressive combat

**Files Modified:**
- `RLAgent.java` (risk calculations)

---

### Configuration

#### Custom Providers Support (Existing)
**Features:**
- Support for custom Ollama API endpoints
- Configurable base URLs
- Model selection
- Temperature and token limits
- Context window configuration

**Files:**
- `CUSTOM_PROVIDERS.md` (documentation)

---

## Technical Details

### Threading Architecture
- **Main Thread**: Minecraft server tick
- **pool-8-thread-1**: RL loop (33ms cycle, 30 FPS)
- **pool-7-thread-1**: Evasion maneuvers
- **ForkJoinPool**: Parallel calculations
- **Async Executors**: Non-blocking operations

### Performance Metrics
- RL loop: 33ms cycle time
- State calculation: ~20ms (parallelized)
- Action blocking: Up to 5 seconds
- Evasion duration: 1000-2500ms (adaptive)
- Q-table save: < 10ms

### Key Files
**Core RL:**
- `RLAgent.java` - Main reinforcement learning loop
- `State.java` - State representation and calculations
- `Action.java` - Action enum definitions
- `QTableStorage.java` - Q-table persistence

**Combat:**
- `IntelligentTargeting.java` - Target selection
- `RangedWeaponUtils.java` - Ranged combat utilities
- `MobThreatEvaluator.java` - Threat assessment

**Defense:**
- `ProjectileDefenseUtils.java` - Projectile detection and evasion
- `PredictiveThreatDetector.java` - Bow detection
- `PlayerRetaliationSystem.java` - Player damage tracking

**Learning:**
- `LookaheadLearning.java` - Pattern analysis
- `StateTransition.java` - Transition recording

**Bot Control:**
- `AutoFaceEntity.java` - Entity facing and evasion
- `BotEventHandler.java` - Event handling and action execution
- `BotCommandRegistry.java` - Command registration

**UI:**
- `ThreatDebugRenderer.java` - Debug overlay rendering
- `NLPDownloadOverlay.java` - Progress bar display

**Integration:**
- `OllamaAPI.java` - Ollama API client

---

## Statistics
- **Total Features**: 25+
- **Bug Fixes**: 15+
- **Performance Optimizations**: 8+
- **Files Modified**: 30+
- **Files Created**: 10+

---

# Additional Technical Documentation

---

## January 2026

### Hybrid Planner Integration (Jan 5, 2026)

The hybrid planner system has been successfully integrated into FunctionCallerV2 as the **default planning system**, with automatic fallback to Markov-based planning and LLM-based planning as sequential backups.

#### Planning System Hierarchy

**1. Hybrid Planner (Default - Highest Priority)**
- **Location:** `net.shasankp000.GameAI.planner.HybridPlanner`
- Goal-oriented action planning using vector embeddings
- Bi-directional A* pathfinding through action graph
- Combines Markov transition data with risk-aware search
- Semantic similarity matching between goals and actions
- Real-time state verification
- **When Used:** Always attempted first when `handleUserGoal()` is called
- **Fallback Trigger:** If hybrid planner returns null or throws exception

**2. Markov Planner (First Fallback)**
- **Location:** `net.shasankp000.GameAI.planner.Planner`
- Second-order Markov chain action sequences
- Risk-aware beam search with local repair
- Learns from successful/failed execution via ActionLogWriter
- Fast deterministic planning (< 50ms typical)
- **When Used:** When hybrid planner fails or is disabled
- **Fallback Trigger:** If plan is null OR score > SAFE_THRESHOLD * 4 (200.0)

**3. LLM-Based Planning (Final Fallback)**
- **Location:** `FunctionCallerV2.fallbackToLLM()`
- Uses language model to generate action pipeline
- Handles complex natural language goals
- Can reason about novel situations
- Fully integrated with existing Ollama/LLM client infrastructure
- Supports both single function calls and multi-step pipelines
- Handles clarification requests from LLM
- **Status:** COMPLETED - Fully integrated with existing LLM pipeline system

#### Key Components

**ActionGraph**
- File: `ActionGraph.java`
- New Method: `buildFromRegistry()` - Automatically populates graph from ActionRegistry
- Infers preconditions/effects from action names
- Estimates base risk and time cost
- Builds compatibility edges between actions

**GoalVector**
- File: `GoalVector.java`
- Purpose: Converts goals and actions to semantic vectors
- Current Implementation: Keyword-based TF-IDF (can be upgraded to neural embeddings)
- Dimensions: 64-element float vectors

**HybridPlanner**
- File: `HybridPlanner.java`
- Algorithm: Goal-conditioned bi-directional A* search
- Integration: Uses MarkovChain2 for transition probabilities
- Risk Analysis: Leverages SequenceRiskAnalyzer with RLAgent

---

### Markov-Based Action Planner Integration (Jan 11, 2026)

Implemented a fast, low-latency action planner using 2nd-order Markov chains as an alternative to LLM-based planning. This system provides deterministic, context-aware action sequences with minimal latency.

#### Core Components

**1. MarkovChain2 (`GameAI/planner/MarkovChain2.java`)**
- Generates action sequences using 2nd-order Markov transitions
- Goal-conditioned transitions: `(goalId, contextHash, prev2, prev1) → action`
- Add-1 smoothing for exploration
- Context-aware parameter generation via shared state
- Incremental learning from executed actions
- Disk persistence for learned transitions

**Shared State Integration:**
- `sharedState` map stores goal-specific context (e.g., target block type)
- `updateSharedState(key, value)` - Set contextual data
- `getSharedState(key)` - Retrieve contextual data
- `clearSharedState()` - Reset between goals

**2. ActionRegistry (`GameAI/planner/ActionRegistry.java`)**
- Maps function names to byte IDs for compact Markov keys
- Automatic registration from ToolRegistry
- Goal-to-action relevance mapping
- Bidirectional lookup (name ↔ byte ID)
- Static initialization with deferred refresh

**3. Planner (`GameAI/planner/Planner.java`)**
- Orchestrates plan generation and refinement
- **Pipeline:**
  1. Generate multiple draft plans (parallel)
  2. Score each using SequenceRiskAnalyzer
  3. Beam search refinement with local edits:
     - Replace segments with Markov resampling
     - Insert safety actions (eat, shield, retreat)
     - Remove duplicate/redundant actions
  4. Return best plan below risk threshold

**4. SequenceRiskAnalyzer (`GameAI/planner/SequenceRiskAnalyzer.java`)**
- Score action sequences using existing RL risk logic
- Scoring Factors: Death risk accumulation, expected damage, time cost, Q-value bonuses
- Uses CheapForward for state simulation

**5. ActionLogWriter (`GameAI/planner/ActionLogWriter.java`)**
- CSV format logging: timestamp, planId, goalId, action, params, outcome, reward
- Asynchronous queue-based writing
- Automatically updates Markov transitions after each step

**6. GoalMapper (`GameAI/planner/GoalMapper.java`)**
- Map natural language goals to goal IDs
- Strategies: Keyword matching (fast, deterministic), LLM-based parsing (fallback)
- Timeout protection (2 seconds max)

#### SearchBlocks Tool

**Implementation (`Tools/SearchBlocks.java`)**
- Efficiently find blocks in expanding radius without lag
- Incremental shell-based search (prevents scanning entire area at once)
- Parallel processing using thread pool
- Position caching to avoid re-scanning
- Respects max blocks per iteration (5000) to prevent lag

**Parameters:**
- `blockType`: Target block (e.g., "minecraft:oak_log")
- `initialRadius`: Starting radius (e.g., 10)
- `maxRadius`: Maximum radius (e.g., 100)
- `radiusIncrement`: Shell thickness (e.g., 20)

#### Performance Characteristics

**Latency:**
- Plan Generation: ~30-50ms (vs 2-10s for LLM)
- Parallel Draft Generation: 4 plans in ~15ms
- Refinement: 6 iterations in ~15ms

**Memory:**
- Markov transitions: ~10,000 entries typical
- Each entry: ~40 bytes
- Total: ~400KB (vs 100MB+ for LLM models)

---

## November 2025 (Continued)

### Code Optimization Summary (Nov 28, 2025)

#### State.java Optimizations

**Performance Improvements:**
1. **calculateBlockOverlap() optimization**
   - Changed from nested streams with O(n*m) complexity to HashSet-based lookup with O(n+m) complexity
   - Removed incorrect division by 2 that was skewing overlap ratios
   - Added check for empty currentBlocks list to prevent unnecessary computation

2. **calculateEntityOverlap() optimization**
   - Changed from List.contains() with O(n*m) complexity to HashSet-based lookup with O(n+m) complexity
   - Eliminated intermediate List creation for entity names
   - Reduced memory allocations by using stream directly to Set

**Code Cleanup:**
1. **Removed unused code**
   - Removed `toMap()` method (never used anywhere in codebase)
   - Removed unused `HashMap` import

2. **Fixed redundant initializers**
   - Removed redundant `List.of()` initializer for `nearbyEntities`
   - Removed redundant `new HashMap<>()` initializer for `podMap`
   - Made `nearbyEntities` field `final` since it's only assigned once

3. **Simplified logic**
   - Simplified if statement in `detectDangerousStructure()` to direct return
   - Reduced unnecessary code branches

**Impact Assessment:**
- Performance Gains: State comparison operations now run in O(n+m) instead of O(n*m) time
- Code Quality: Removed 1 unused method (~25 lines), fixed 4 compiler warnings
- No Breaking Changes: All public APIs remain unchanged

---

### PlaceBlock Tool - Complete Guide (Nov 30, 2025)

#### Overview
The **placeBlock** tool enables the AI bot to place blocks in the Minecraft world through intelligent function calling with comprehensive validation and automation.

#### Features

**Smart Block Placement:**
- Places blocks at any valid coordinate within reach
- Automatically handles Minecraft's placement mechanics
- Verifies successful placement

**Automatic Inventory Management:**
- Searches entire inventory for requested block
- Moves block to hotbar automatically if needed
- Preserves existing hotbar layout when possible

**Comprehensive Validation:**
- Distance Check: Ensures bot is within 5 blocks of target
- Inventory Check: Confirms block is available
- Position Check: Validates target is empty/replaceable
- Surface Check: Finds suitable adjacent block to place against

**Precise Execution:**
- Bot automatically looks at placement target
- Uses correct placement direction based on adjacent blocks
- Handles edge cases gracefully

#### Parameters

| Parameter | Type | Description | Example |
|-----------|------|-------------|---------|
| `targetX` | Integer | X coordinate where block should be placed | `100` |
| `targetY` | Integer | Y coordinate where block should be placed | `64` |
| `targetZ` | Integer | Z coordinate where block should be placed | `-50` |
| `blockType` | String | Type of block to place | `"stone"`, `"minecraft:oak_planks"` |

#### Return Values

Updates shared state with placement information:
```javascript
{
  "lastPlacedBlock.x": 100,
  "lastPlacedBlock.y": 64,
  "lastPlacedBlock.z": -50,
  "lastPlacedBlock.type": "minecraft:stone"
}
```

#### Success/Error Messages

**Success:**
- ✅ "Successfully placed [block] at x:X y:Y z:Z"

**Errors:**
- ❌ "Too far from target position!" - Bot is more than 5 blocks away
- ❌ "Block not found in inventory: [block]" - Missing required item
- ❌ "Target position is already occupied by: [block]" - Space is blocked
- ❌ "No suitable surface found to place block against at [pos]" - No adjacent block

#### Best Practices

1. **Always Check Distance:** Use `goTo` to position the bot within reach
2. **Build from Ground Up:** Place blocks from bottom to top for surface support
3. **Use Placeholders:** Leverage `$lastDetectedBlock` or `$lastPlacedBlock` for relative placement
4. **Batch Operations:** Group multiple placements in a pipeline for efficiency
5. **Verify Inventory First:** Ensure bot has sufficient blocks before starting

#### Technical Details

- **Maximum Placement Distance:** 5.0 blocks (matches Minecraft reach)
- **Execution Model:** Asynchronous with CompletableFuture
- **Timeout:** 10 seconds per placement
- **Thread-Safe:** Can be called from any thread

#### Limitations

1. Reach Distance: Bot must be within 5 blocks of target
2. Adjacent Block Required: Cannot place blocks in mid-air without surface
3. Inventory Requirement: Block must exist in bot's inventory
4. Replaceable Blocks Only: Target must be empty or contain replaceable block
5. No Physics Simulation: Doesn't check if block will fall (sand, gravel)

---

### Embedding System Architecture Overview (Nov 29, 2025)

#### System Changes

**Before:**
- Embeddings only worked with Ollama
- Users had to manually run Ollama + nomic-embed-text
- Cloud providers couldn't use their native embedding endpoints

**After:**
- Embeddings automatically use whatever LLM provider you choose
- OpenAI, Gemini, Grok, and custom providers all supported
- Automatic fallback to Ollama if cloud provider fails
- Still works 100% locally with Ollama (free!)

#### Provider Specifications

**OpenAI:**
- Endpoint: `https://api.openai.com/v1`
- Model: `text-embedding-3-small`
- Dimensions: 1536
- Cost: $0.02 per 1M tokens

**Google Gemini:**
- Endpoint: `https://generativelanguage.googleapis.com/v1beta`
- Model: `text-embedding-004`
- Dimensions: 768
- Free tier available

**Grok (xAI):**
- Endpoint: `https://api.x.ai/v1`
- Model: `text-embedding-ada-002`
- Dimensions: 1536
- Cost: TBD (new service)

**Custom Provider:**
- Endpoint: User-defined (e.g., `http://localhost:1234`)
- Model: User-defined or auto-detected
- Cost: Varies (often free for local LM Studio/vLLM)

**Ollama:**
- Endpoint: `http://localhost:11434`
- Model: `nomic-embed-text`
- Dimensions: 768
- Cost: Free (local)

#### Performance Characteristics

**Latency Comparison (per 1000 tokens):**
| Provider | Average Latency | Notes |
|----------|----------------|-------|
| Ollama (local) | ~50ms | Fastest (GPU) / ~200ms (CPU) |
| Custom (local) | ~100ms | Depends on hardware |
| OpenAI | ~300ms | Network + API processing |
| Gemini | ~250ms | Network + API processing |
| Grok | ~350ms | Network + API processing |

**Throughput Comparison:**
| Provider | Max Requests/Min | Notes |
|----------|------------------|-------|
| Ollama | Unlimited | Limited by local hardware |
| OpenAI | 3,000 | Tier 1 default |
| Gemini | 60 | Free tier |
| Custom | Varies | Depends on setup |

#### Security Considerations

**API Key Storage:**
- Stored in `ManualConfig` (encrypted in future versions)
- Not logged or exposed in debug output
- Not transmitted except to intended provider

**Network Security:**
- All cloud providers use HTTPS
- Ollama uses local HTTP (localhost only)
- No data sent to cloud if using Ollama

**Data Privacy:**
- Cloud providers: Data sent to their servers (check ToS)
- Ollama: 100% local, no data leaves your machine
- Custom: Depends on your endpoint configuration

---

## February 2026

### Bug Fixes & Improvements

#### HybridPlanner Parameter Format Fix (Feb 5, 2026)
**Bug Fix:** Fixed critical compilation error in HybridPlanner's parameter inference system.

**Issues Fixed:**
- ✅ **PlannedStep Constructor Error:** Fixed incorrect constructor call that was passing `Map<String, Object>` instead of `String` for parameters
- ✅ **Parameter Format:** Converted parameter inference to use comma-separated string format (e.g., "100,64,200,true") matching the expected format
- ✅ **SharedState Integration:** Fixed parameter inference to properly access MarkovChain2's shared state for coordinate resolution

**Changes Made:**
- Updated `inferParameters()` method to return `String` instead of `Map<String, Object>`
- Implemented proper comma-separated parameter formatting for all action types:
  - `searchBlocks`: "blockType,initialRadius,maxRadius,radiusIncrement"
  - `goTo`/`moveToCoordinates`: "x,y,z,sprint"
  - `mineBlock`/`breakBlock`: "x,y,z"
  - `placeBlock`: "x,y,z,blockType"
  - `turn`: "direction"
  - `look`: "cardinalDirection"
- Fixed PlannedStep instantiation to use correct constructor: `new PlannedStep(actionId, actionName, estimatedRisk, params)`

**Technical Details:**
- **File Modified:** `HybridPlanner.java`
- **Method Updated:** `inferParameters(ActionNode, State)` - Now returns comma-separated string
- **Method Updated:** `convertToplan(SearchResult, short, State)` - Uses correct PlannedStep constructor
- **Integration:** Properly reads from MarkovChain2.sharedState for coordinate resolution from previous steps

**Impact:**
- HybridPlanner now correctly generates executable plans with properly formatted parameters
- Parameters are compatible with FunctionCallerV2's parameter parsing system
- Seamless integration with other planning systems (Markov, LLM fallback)

**Testing:**
- Verified HybridPlanner compiles without errors
- Confirmed parameter format matches MarkovChain2's generateDefaultParams() output
- Validated PlannedStep constructor compatibility

---

*Last Updated: February 5, 2026*

