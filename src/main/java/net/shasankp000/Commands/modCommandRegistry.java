package net.shasankp000.Commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.shasankp000.ChatUtils.ChatUtils;
import net.shasankp000.DangerZoneDetector.DangerZoneDetector;
import net.shasankp000.Database.QTableExporter;
import net.shasankp000.Entity.*;
import net.shasankp000.FilingSystem.LLMClientFactory;
import net.shasankp000.GameAI.BotEventHandler;
import net.shasankp000.OllamaClient.ollamaClient;
import net.shasankp000.PathFinding.ChartPathToBlock;
import net.shasankp000.PathFinding.PathFinder;
import net.shasankp000.PathFinding.PathTracer;
import net.shasankp000.PathFinding.Segment;
import net.shasankp000.PlayerUtils.*;
import net.shasankp000.ServiceLLMClients.LLMClient;
import net.shasankp000.ServiceLLMClients.LLMServiceHandler;
import net.shasankp000.WorldUitls.isFoodItem;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


import static net.shasankp000.PathFinding.PathFinder.*;
import static net.minecraft.server.command.CommandManager.literal;
import net.shasankp000.PacketHandler.InputPacketHandler;

public class modCommandRegistry {

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    public static boolean isTrainingMode = false;
    public static String botName = "";
    public static final Logger LOGGER = LoggerFactory.getLogger("mod-command-registry");


    public record BotStopTask(MinecraftServer server, ServerCommandSource botSource,
                                  String botName) implements Runnable {

        @Override
        public void run() {

            stopMoving(server, botSource, botName);
            LOGGER.info("{} has stopped walking!", botName);


        }
    }


    public static void register() {
        // Register threat debug command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ThreatDebugCommand.register(dispatcher);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("bot")
                        .then(literal("spawn")
                                .then(CommandManager.argument("bot_name", StringArgumentType.string())
                                        .then(CommandManager.argument("mode", StringArgumentType.string())
                                                .executes(context -> {

                                                    String spawnMode = StringArgumentType.getString(context, "mode");

                                                    spawnBot(context, spawnMode);


                                                    return 1;
                                                })
                                        )
                                )
                        )
                        .then(literal("walk")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("till", IntegerArgumentType.integer())
                                                .executes(context -> { botWalk(context); return 1; })
                                        )
                                )
                        )
                        .then(literal("jump")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> { botJump(context); return 1; })
                                )
                        )
                        .then(literal("teleport_forward")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> { teleportForward(context); return 1; })
                                )
                        )
                        .then(literal("test_chat_message")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> { testChatMessage(context); return 1; })
                                )
                        )
                        .then(literal("go_to")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                                .then(CommandManager.argument("sprint", StringArgumentType.string())
                                                        .executes(context -> { botGo(context); return 1; })
                                                )
                                        )
                                )
                        )
                        .then(literal("send_message_to")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("message", StringArgumentType.greedyString())
                                                .executes(context -> {

                                                    ollamaClient.execute(context);

                                                     return 1;

                                                })
                                        )
                                )
                        )
                        .then(literal("detect_entities")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> {
                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                            if (bot != null) {
                                                RayCasting.detect(bot);
                                            }
                                            return 1;
                                        })
                                )
                        )
                        .then(literal("get_block_map")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("vertical", IntegerArgumentType.integer())
                                                .then(CommandManager.argument("horizontal", IntegerArgumentType.integer())
                                                        .executes(context -> {
                                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                                            int y = IntegerArgumentType.getInteger(context, "vertical");
                                                            int x = IntegerArgumentType.getInteger(context, "horizontal");

                                                            InternalMap internalMap = new InternalMap(bot, y, x);
                                                            internalMap.updateMap();
                                                            internalMap.printMap();
                                                            return 1;
                                                        })
                                                )
                                        )

                                )

                        )
                        .then(literal("start_autoface")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> {
                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                            LOGGER.info("Manually starting autoface for bot: {}", bot.getName().getString());
                                            AutoFaceEntity.startAutoFace(bot);
                                            ChatUtils.sendSystemMessage(context.getSource(), "AutoFace started for " + bot.getName().getString());
                                            return 1;
                                        })
                                )
                        )

                        .then(literal("detect_blocks")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("block_type", StringArgumentType.string())
                                                .executes(context -> {

                                                    ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                                    String blockType = StringArgumentType.getString(context, "block_type");

                                                    BlockPos outPutPos = blockDetectionUnit.detectBlocks(bot, blockType);

                                                    LOGGER.info("Detected Block: {} at x={}, y={}, z={}", blockType, outPutPos.getX(), outPutPos.getY(), outPutPos.getZ());
                                                    blockDetectionUnit.setIsBlockDetectionActive(false);

                                                    return 1;
                                                })
                                        )
                                )
                        )

                        .then(literal("turn")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("direction", StringArgumentType.string())
                                                .executes(context -> {

                                                    ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                                    MinecraftServer server = bot.getServer();
                                                    assert server != null;
                                                    String direction = StringArgumentType.getString(context, "direction");

                                                    switch (direction) {
                                                        case "left", "right", "back" -> {
                                                            turnTool.turn(bot.getCommandSource().withSilent().withMaxLevel(4), direction);

                                                            LOGGER.info("Now facing {} which is in {} in {} axis", direction, bot.getFacing().getName(), bot.getFacing().getAxis().asString());
                                                        }
                                                        default -> {
                                                            server.execute(() -> {
                                                                ChatUtils.sendChatMessages(bot.getCommandSource().withSilent().withMaxLevel(4), "Invalid parameters! Accepted parameters: left, right, back only!");
                                                            });
                                                        }
                                                    }

                                                    return 1;
                                                })
                                        )
                                )
                        )


                        .then(literal("chart_path_to_block")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("block_type", StringArgumentType.string())
                                                .then(CommandManager.argument("x", IntegerArgumentType.integer())
                                                        .then(CommandManager.argument("y", IntegerArgumentType.integer())
                                                                .then(CommandManager.argument("z", IntegerArgumentType.integer())
                                                                        .executes(context -> {

                                                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                                                            MinecraftServer server = bot.getServer();
                                                                            assert server != null;
                                                                            String blockType = StringArgumentType.getString(context, "block_type");
                                                                            int x = IntegerArgumentType.getInteger(context, "x");
                                                                            int y = IntegerArgumentType.getInteger(context, "y");
                                                                            int z = IntegerArgumentType.getInteger(context, "z");

                                                                            BlockPos targetPos = new BlockPos(x, y, z);

                                                                            ChartPathToBlock.chart(bot, targetPos, blockType);

                                                                            return 1;
                                                                        })
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )

                        .then(literal("shoot_arrow")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("debug", StringArgumentType.string())
                                                .executes(context -> {

                                                    ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                                    String debugMode = StringArgumentType.getString(context, "debug");
                                                    MinecraftServer server = bot.getServer();
                                                    assert server != null;
                                                    ServerCommandSource botSource = bot.getCommandSource().withSilent().withMaxLevel(4);

                                                    // Check if bot can shoot (checks entire inventory now, not just equipped)
                                                    if (!RangedWeaponUtils.hasBowOrCrossbow(bot)) {
                                                        LOGGER.info("Bot does not have a bow or crossbow to shoot with.");
                                                        if (debugMode.equals("true")) {
                                                            ChatUtils.sendChatMessages(botSource, "I don't have a bow or crossbow!");
                                                        }
                                                        return 0;
                                                    }

                                                    // Prepare ammo first (move items to correct slots if needed)
                                                    String ammoType;
                                                    if (RangedWeaponUtils.hasCrossbow(bot)) {
                                                        ammoType = RangedWeaponUtils.prepareCrossbowAmmo(bot, server, botSource);
                                                        LOGGER.info("Prepared crossbow with ammo: {}", ammoType);
                                                    } else if (RangedWeaponUtils.hasBow(bot)) {
                                                        ammoType = RangedWeaponUtils.prepareBowAmmo(bot, server, botSource);
                                                        LOGGER.info("Prepared bow with ammo: {}", ammoType);
                                                    } else {
                                                        ammoType = RangedWeaponUtils.getAmmoType(bot);
                                                    }

                                                    if (ammoType == null) {
                                                        if (debugMode.equals("true")) {
                                                            ChatUtils.sendChatMessages(botSource, "I don't have any arrows or firework rockets!");
                                                        }

                                                        return 0;
                                                    }

                                                    // Detect nearby hostile entities AND hostile players within 40 blocks
                                                    List<Entity> nearbyEntities = AutoFaceEntity.detectNearbyEntities(bot, 40);
                                                    List<Entity> hostileEntities = nearbyEntities.stream()
                                                            .filter(entity -> {
                                                                // Include hostile mobs
                                                                if (entity instanceof HostileEntity) {
                                                                    return true;
                                                                }
                                                                // Include hostile players (tracked by retaliation system)
                                                                if (entity instanceof net.minecraft.entity.player.PlayerEntity player &&
                                                                    !player.getUuid().equals(bot.getUuid())) {
                                                                    return net.shasankp000.PlayerUtils.PlayerRetaliationTracker.isPlayerHostile(bot, player);
                                                                }
                                                                return false;
                                                            })
                                                            .toList();

                                                    if (hostileEntities.isEmpty()) {
                                                        if (debugMode.equals("true")) {
                                                            ChatUtils.sendChatMessages(botSource, "No hostile entities or players detected within 40 blocks!");
                                                        }

                                                        return 0;
                                                    }

                                                    // ✨ INTELLIGENT TARGETING: Use risk analysis to prioritize threats
                                                    Entity target = selectHighestThreatTarget(bot, hostileEntities, debugMode.equals("true"), botSource);

                                                    if (target == null) {
                                                        ChatUtils.sendChatMessages(botSource, "No suitable target found for shooting!");
                                                        return 0;
                                                    }

                                                    double distance = bot.getPos().distanceTo(target.getPos());
                                                    String targetName = target.getName().getString();

                                                    if (debugMode.equals("true")) {
                                                        ChatUtils.sendChatMessages(botSource, "Target acquired: " + targetName + " at " + String.format("%.1f", distance) + " blocks");
                                                    }

                                                    // Pause autoface to prevent interruption
                                                    AutoFaceEntity.isShooting = true;
                                                    LOGGER.info("Paused autoface for shooting");

                                                    // Switch to bow/crossbow if not already equipped
                                                    int weaponSlot = RangedWeaponUtils.getBowOrCrossbowSlot(bot);
                                                    if (weaponSlot != -1 && bot.getInventory().selectedSlot != (weaponSlot - 1)) {
                                                        server.getCommandManager().executeWithPrefix(botSource, "/player " + bot.getName().getString() + " hotbar " + weaponSlot);
                                                        LOGGER.info("Switched to ranged weapon in slot {}", weaponSlot);
                                                        // Small delay for weapon switch
                                                        try {
                                                            Thread.sleep(100);
                                                        } catch (InterruptedException e) {
                                                            LOGGER.error("Weapon switch interrupted");
                                                        }
                                                    }

                                                    // Determine projectile speed based on ammo type
                                                    double projectileSpeed = ammoType.equals("firework") ? 1.5 : 3.0;
                                                    String ammoTypeName = ammoType.equals("firework") ? "firework rocket" : "arrow";

                                                    // Calculate if target is moving fast
                                                    boolean isMovingFast = RangedWeaponUtils.isTargetMovingFast(target);

                                                    // ALWAYS use ballistic aim with gravity compensation
                                                    Vec3d aimPosition;
                                                    if (isMovingFast) {
                                                        // Use lead compensation for fast-moving targets
                                                        aimPosition = RangedWeaponUtils.calculateLeadPosition(target, projectileSpeed);
                                                        LOGGER.info("Applied lead compensation for fast-moving target");
                                                    } else {
                                                        // For stationary targets, aim at center mass (chest height)
                                                        aimPosition = target.getPos().add(0, target.getHeight() * 0.6, 0);
                                                    }

                                                    // Calculate ballistic trajectory with gravity compensation
                                                    float[] aimAngles = RangedWeaponUtils.calculateAimAngles(bot, aimPosition);
                                                    bot.setYaw(aimAngles[0]);
                                                    bot.setPitch(aimAngles[1]);

                                                    LOGGER.info("Aiming at {} at distance {} using {} (ballistic trajectory)",
                                                        targetName, String.format("%.1f", distance), ammoTypeName);

                                                    // Shoot (use item to draw bow/charge crossbow, then release)
                                                    String weaponName = bot.getMainHandStack().getItem().getName().getString().toLowerCase();
                                                    boolean isCrossbow = weaponName.contains("crossbow");

                                                    // ⚡ DYNAMIC DRAW TIME: Adjust based on distance to target
                                                    int drawTime;
                                                    if (isCrossbow) {
                                                        drawTime = 25; // Crossbow always full charge
                                                    } else {
                                                        // BOW: Calculate optimal draw time for distance
                                                        drawTime = net.shasankp000.PlayerUtils.WeaponUtils.calculateOptimalDrawTime(distance);
                                                        projectileSpeed = net.shasankp000.PlayerUtils.WeaponUtils.calculateProjectileSpeed(drawTime);

                                                        LOGGER.info("⚡ Dynamic bow draw time: {} ticks for {}m (speed: {})",
                                                            drawTime, String.format("%.1f", distance), String.format("%.2f", projectileSpeed));
                                                    }

                                                    // Set shooting target BEFORE starting to lock autoface onto it
                                                    AutoFaceEntity.setShootingTarget(target);

                                                    if (debugMode.equals("true")) {
                                                        ChatUtils.sendChatMessages(botSource, "Shooting target set to " + targetName);
                                                        ChatUtils.sendChatMessages(botSource, "Drawing " + (isCrossbow ? "crossbow" : "bow") + " with " + ammoTypeName + "...");

                                                    }

                                                    // Start drawing bow/charging crossbow
                                                    String playerName = bot.getName().getString();
                                                    server.getCommandManager().executeWithPrefix(botSource, "/player " + playerName + " use continuous");
                                                    LOGGER.info("Started drawing weapon");

                                                    // Schedule release after draw time
                                                    Entity finalTarget = target;
                                                    String finalAmmoType = ammoType;
                                                    double finalProjectileSpeed = projectileSpeed;
                                                    scheduler.schedule(() -> {
                                                        // Re-aim just before releasing (in case target moved)
                                                        if (finalTarget.isAlive()) {
                                                            Vec3d finalAimPosition;
                                                            if (isMovingFast) {
                                                                // Recalculate lead for moving targets
                                                                finalAimPosition = RangedWeaponUtils.calculateLeadPosition(finalTarget, finalProjectileSpeed);
                                                            } else {
                                                                // Re-aim at center mass for stationary targets
                                                                finalAimPosition = finalTarget.getPos().add(0, finalTarget.getHeight() * 0.6, 0);
                                                            }
                                                            float[] reAimAngles = RangedWeaponUtils.calculateAimAngles(bot, finalAimPosition);
                                                            bot.setYaw(reAimAngles[0]);
                                                            bot.setPitch(reAimAngles[1]);
                                                        }

                                                        // Release arrow/bolt/firework
                                                        server.getCommandManager().executeWithPrefix(botSource, "/player " + playerName + " use");
                                                        if (debugMode.equals("true")) {
                                                            ChatUtils.sendChatMessages(botSource, ammoTypeName.substring(0, 1).toUpperCase() + ammoTypeName.substring(1) + " released at " + targetName + "!");
                                                        }
                                                        LOGGER.info("Shot {} at {} from {} blocks away", ammoTypeName, targetName, distance);

                                                        // Clear shooting target after 1 second (no arrow tracking - it causes lag)
                                                        scheduler.schedule(() -> {
                                                            AutoFaceEntity.clearShootingTarget();
                                                            if (debugMode.equals("true")) {
                                                                ChatUtils.sendChatMessages(botSource, "Shot complete");
                                                            }
                                                            LOGGER.info("Shooting complete, autoface resumed");

                                                            // ✅ Signal action completion
                                                            BotEventHandler.completeAction(playerName);
                                                        }, 1000, TimeUnit.MILLISECONDS);

                                                    }, drawTime * 50, TimeUnit.MILLISECONDS);

                                                    return 1;
                                                })
                                        )
                                )
                        )

                        .then(literal("reset_autoface")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> {

                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                            MinecraftServer server = bot.getServer();
                                            assert server != null;
                                            blockDetectionUnit.setIsBlockDetectionActive(false);
                                            PathTracer.flushAllMovementTasks();
                                            AutoFaceEntity.setBotExecutingTask(false);
                                            AutoFaceEntity.isBotMoving = false;

                                            server.execute(() -> {
                                                ChatUtils.sendChatMessages(bot.getCommandSource().withSilent().withMaxLevel(4), "Autoface module reset complete.");
                                            });

                                            return 1;
                                        })

                                )
                        )

                        .then(literal("plan")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("goal", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                                    String goal = StringArgumentType.getString(context, "goal");
                                                    MinecraftServer server = bot.getServer();
                                                    assert server != null;
                                                    ServerCommandSource botSource = bot.getCommandSource().withSilent().withMaxLevel(4);

                                                    // Initialize planner if not already done
                                                    net.shasankp000.GameAI.RLAgent rlAgent = BotEventHandler.getRLAgent(bot);
                                                    if (rlAgent == null) {
                                                        LOGGER.error("RLAgent not initialized for bot {}", bot.getName().getString());
                                                        ChatUtils.sendChatMessages(botSource, "Error: AI system not ready");
                                                        return 0;
                                                    }

                                                    net.shasankp000.FunctionCaller.FunctionCallerV2.initializePlanner(bot, rlAgent);

                                                    // Get current state
                                                    net.shasankp000.GameAI.State currentState = rlAgent.getCurrentState(bot);

                                                    // Send initial feedback
                                                    ChatUtils.sendChatMessages(botSource, "Planning: " + goal + "...");

                                                    // Execute plan asynchronously
                                                    net.shasankp000.FunctionCaller.FunctionCallerV2.handleUserGoal(goal, currentState, bot, rlAgent, botSource)
                                                            .thenAccept(success -> {
                                                                server.execute(() -> {
                                                                    if (success) {
                                                                        ChatUtils.sendChatMessages(botSource, "✓ Plan executed successfully!");
                                                                        LOGGER.info("[planner] ✓ Goal '{}' completed", goal);
                                                                    } else {
                                                                        ChatUtils.sendChatMessages(botSource, "✗ Plan execution failed");
                                                                        LOGGER.warn("[planner] ✗ Goal '{}' failed", goal);
                                                                    }
                                                                });
                                                            })
                                                            .exceptionally(ex -> {
                                                                server.execute(() -> {
                                                                    ChatUtils.sendChatMessages(botSource, "Error: " + ex.getMessage());
                                                                    LOGGER.error("[planner] Exception during goal execution", ex);
                                                                });
                                                                return null;
                                                            });

                                                    return 1;
                                                })
                                        )
                                )
                        )

                        .then(literal("mine_block")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("block_type", StringArgumentType.string())
                                                .then(CommandManager.argument("x", IntegerArgumentType.integer())
                                                        .then(CommandManager.argument("y", IntegerArgumentType.integer())
                                                                .then(CommandManager.argument("z", IntegerArgumentType.integer())
                                                                        .executes(context -> {

                                                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                                                            int x = IntegerArgumentType.getInteger(context, "x");
                                                                            int y = IntegerArgumentType.getInteger(context, "y");
                                                                            int z = IntegerArgumentType.getInteger(context, "z");
                                                                            MiningTool.mineBlock(bot, new BlockPos(x, y, z));
                                                                            
                                                                            return 1;
                                                                        })
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )


                        .then(literal("use-key")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("key", StringArgumentType.string())
                                                .executes(context -> {
                                                    MinecraftServer server = context.getSource().getServer();

                                                    ServerCommandSource serverSource = server.getCommandSource();
                                                    String inputKey = StringArgumentType.getString(context, "key");

                                                    switch (inputKey) {
                                                        case "W":
                                                            InputPacketHandler.manualPacketPressWKey(context);
                                                            break;
                                                        case "S":
                                                            InputPacketHandler.manualPacketPressSKey(context);
                                                            break;
                                                        case "A":
                                                            InputPacketHandler.manualPacketPressAKey(context);
                                                            break;
                                                        case "D":
                                                            InputPacketHandler.manualPacketPressDKey(context);
                                                            break;
                                                        case "Sneak":
                                                            InputPacketHandler.manualPacketSneak(context);
                                                            break;
                                                        case "LSHIFT":
                                                            InputPacketHandler.manualPacketSneak(context);
                                                            break;
                                                        case "Sprint":
                                                            InputPacketHandler.manualPacketSprint(context);
                                                            break;
                                                        default:
                                                            ChatUtils.sendSystemMessage(serverSource, "This key is not registered.");
                                                            break;
                                                    }

                                                    return 1;
                                                })
                                        )

                                )
                        )

                        .then(literal("look")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("bot_name", StringArgumentType.string())
                                                .then(CommandManager.argument("direction", StringArgumentType.string())
                                                        .executes(context -> {

                                                            MinecraftServer server = context.getSource().getServer();

                                                            ServerCommandSource serverSource = server.getCommandSource();

                                                            String botName = StringArgumentType.getString(context, "bot_name");

                                                            ServerPlayerEntity bot = context.getSource().getServer().getPlayerManager().getPlayer(botName);

                                                            String direction = StringArgumentType.getString(context, "direction");

                                                            switch (direction) {

                                                                case("north"):
                                                                    InputPacketHandler.BotLookController.lookInDirection(bot, Direction.NORTH);
                                                                    break;

                                                                case("south"):
                                                                    InputPacketHandler.BotLookController.lookInDirection(bot, Direction.SOUTH);
                                                                    break;

                                                                case("east"):
                                                                    InputPacketHandler.BotLookController.lookInDirection(bot, Direction.EAST);
                                                                    break;

                                                                case("west"):
                                                                    InputPacketHandler.BotLookController.lookInDirection(bot, Direction.WEST);
                                                                    break;

                                                                default:
                                                                    ChatUtils.sendSystemMessage(serverSource, "Invalid direction.");
                                                                    break;
                                                            }

                                                            return 1;
                                                        })

                                                )
                                        )

                                )

                        )

                        .then(literal("release-all-keys")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("bot_name", StringArgumentType.string())
                                                .executes(context -> {
                                                    MinecraftServer server = context.getSource().getServer();

                                                    ServerCommandSource serverSource = server.getCommandSource();

                                                    String botName = StringArgumentType.getString(context, "bot_name");

                                                    InputPacketHandler.manualPacketReleaseMovementKey(context);

                                                    ChatUtils.sendSystemMessage(serverSource, "Released all movement keys for bot: " + botName);

                                                    return 1;
                                                })
                                        )

                                )
                        )

                        .then(literal("detectDangerZone")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("lavaRange", IntegerArgumentType.integer())
                                                .then(CommandManager.argument("cliffRange", IntegerArgumentType.integer())
                                                        .then(CommandManager.argument("cliffDepth", IntegerArgumentType.integer())
                                                                .executes(context -> {

                                                                    ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                                                    ServerCommandSource botSource = bot.getCommandSource().withSilent().withMaxLevel(4);
                                                                    MinecraftServer server = botSource.getServer();

                                                                    int lavaRange = IntegerArgumentType.getInteger(context, "lavaRange");     // Range to check for lava blocks
                                                                    int cliffRange = IntegerArgumentType.getInteger(context, "cliffRange");     // Forward range to check for cliffs
                                                                    int cliffDepth = IntegerArgumentType.getInteger(context, "cliffDepth");    // Downward range to check for solid blocks

                                                                    server.execute(() -> {
                                                                        // Putting this part in a thread so that it doesn't hang the game.

                                                                        double dangerDistance = DangerZoneDetector.detectDangerZone(bot, lavaRange, cliffRange, cliffDepth);
                                                                        if (dangerDistance > 0) {
                                                                            System.out.println("Danger detected! Effective distance: " + dangerDistance);
                                                                            ChatUtils.sendChatMessages(botSource, "Danger detected! Effective distance to danger: " + (int) dangerDistance + " blocks");

                                                                        } else {
                                                                            System.out.println("No danger nearby.");
                                                                            ChatUtils.sendChatMessages(botSource, "No danger nearby");
                                                                        }

                                                                    });

                                                                    return 1;
                                                                })
                                                        )
                                                )
                                        )
                                )
                        )


                        .then(literal("getHotbarItems")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> {
                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                            ServerCommandSource botSource = bot.getCommandSource().withSilent().withMaxLevel(4);

                                            List<ItemStack> hotbarItems = hotBarUtils.getHotbarItems(bot);

                                            StringBuilder messageBuilder = new StringBuilder(); // Initialize a StringBuilder

                                            for (int i = 0; i < hotbarItems.size(); i++) {
                                                int slotIndex = i; // Avoid issues with lambda expressions

                                                ItemStack itemStack = hotbarItems.get(slotIndex);

                                                if (itemStack.isEmpty()) {

                                                    messageBuilder.append("Slot ").append(i+1).append(": EMPTY\n"); // Append for empty slots

                                                } else {

                                                    messageBuilder.append("Slot ").append(i+1).append(": ")
                                                            .append(itemStack.getName().getString()) // Add item name
                                                            .append(" (Count: ").append(itemStack.getCount()).append(")\n"); // Add item count

                                                }


                                            }

                                            String finalMessage = messageBuilder.toString();

                                            ChatUtils.sendChatMessages(botSource, finalMessage);


                                            return 1;
                                        })
                                )

                        )

                        .then(literal("getSelectedItem")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> {

                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");

                                            ServerCommandSource botSource = bot.getCommandSource().withSilent().withMaxLevel(4);

                                            String selectedItem = hotBarUtils.getSelectedHotbarItemStack(bot).getItem().getName().getString();

                                            ChatUtils.sendChatMessages(botSource, "Currently selected item: " + selectedItem);

                                            return 1;
                                        })

                                )

                        )

                        .then(literal("getHungerLevel")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> {

                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");

                                            ServerCommandSource botSource = bot.getCommandSource().withSilent().withMaxLevel(4);

                                            int botHungerLevel = getPlayerHunger.getBotHungerLevel(bot);

                                            ChatUtils.sendChatMessages(botSource, "Hunger level: " + botHungerLevel);

                                            return 1;

                                        })
                                )
                        )

                        .then(literal("getOxygenLevel")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> {

                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");

                                            ServerCommandSource botSource = bot.getCommandSource().withSilent().withMaxLevel(4);

                                            int botHungerLevel = getPlayerOxygen.getBotOxygenLevel(bot);

                                            ChatUtils.sendChatMessages(botSource, "Oxygen level: " + botHungerLevel);

                                            return 1;
                                        })
                                )
                        )
                        .then(literal("getHealth")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> {

                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");

                                            ServerCommandSource botSource = bot.getCommandSource().withSilent().withMaxLevel(4);

                                            int botHealthLevel = (int) bot.getHealth();

                                            ChatUtils.sendChatMessages(botSource, "Health level: " + botHealthLevel);

                                            return 1;
                                        })
                                )
                        )

                        .then(literal("isFoodItem")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> {

                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");

                                            ServerCommandSource botSource = bot.getCommandSource().withSilent().withMaxLevel(4);

                                            ItemStack selectedItemStack = hotBarUtils.getSelectedHotbarItemStack(bot);

                                            if (isFoodItem.checkFoodItem(selectedItemStack)) {

                                                ChatUtils.sendChatMessages(botSource, "Currently selected item: " + selectedItemStack.getItem().getName().getString() + " is a food item.");

                                            }

                                            else {

                                                ChatUtils.sendChatMessages(botSource, "Currently selected item: " + selectedItemStack.getItem().getName().getString() + " is not a food item.");

                                            }

                                            return 1;
                                        })
                                )
                        )


                        .then(literal("equipArmor")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> {

                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");

                                            armorUtils.autoEquipArmor(bot);

                                            return 1;
                                        })

                                )
                        )
                        .then(literal("removeArmor")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> {

                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");

                                            armorUtils.autoDeEquipArmor(bot);

                                            return 1;
                                        })

                                )
                        )

                        .then(literal("exportQTableToJSON")
                                .executes(context -> {

                                    MinecraftServer server = context.getSource().getServer(); // gets the minecraft server
                                    ServerCommandSource serverSource = server.getCommandSource();

                                    ChatUtils.sendSystemMessage(serverSource, "Exporting Q-table to JSON. Please wait.... ");

                                    QTableExporter.exportQTable(BotEventHandler.qTableDir + "/qtable.bin", BotEventHandler.qTableDir + "./fullQTable.json");

                                    ChatUtils.sendSystemMessage(serverSource, "Q-table has been successfully exported to a json file at: " + BotEventHandler.qTableDir + "./fullQTable.json" );

                                    return 1;
                                })
                        )

                        .then(literal("stopAllMovementTasks")
                                .executes(context -> {

                                    MinecraftServer server = context.getSource().getServer(); // gets the minecraft server
                                    ServerCommandSource serverSource = server.getCommandSource();
                                    PathTracer.flushAllMovementTasks();

                                    ChatUtils.sendSystemMessage(serverSource, "Flushed all movement tasks");

                                    return 1;

                                })
                        )
        ));
    }


    private static void spawnBot(CommandContext<ServerCommandSource> context, String spawnMode) {
        LOGGER.info("========== SPAWNING BOT IN MODE: {} ==========", spawnMode);

        MinecraftServer server = context.getSource().getServer(); // gets the minecraft server
        BlockPos spawnPos = getBlockPos(context);

        RegistryKey<World> dimType = context.getSource().getWorld().getRegistryKey();

        Vec2f facing = context.getSource().getRotation();

        Vec3d pos = new Vec3d(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());

        GameMode mode = GameMode.SURVIVAL;

        botName = StringArgumentType.getString(context, "bot_name");

        ServerCommandSource serverSource = server.getCommandSource();


        if (spawnMode.equals("training")) {

            createFakePlayer.createFake(
                    botName,
                    server,
                    pos,
                    facing.y,
                    facing.x,
                    dimType,
                    mode,
                    false
            );

            isTrainingMode = true;

            LOGGER.info("Spawned new bot {}!", botName);

            ServerPlayerEntity bot = server.getPlayerManager().getPlayer(botName);

            if (bot!=null) {

                Objects.requireNonNull(bot.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE)).setBaseValue(0.0);

                RespawnHandler.registerRespawnListener(bot);

                AutoFaceEntity.startAutoFace(bot);

            }

            else {
                ChatUtils.sendSystemMessage(serverSource, "Error: " + botName + " cannot be spawned");
            }

            // don't initialize ollama client.

        } else if (spawnMode.equals("play")) {

            createFakePlayer.createFake(
                    botName,
                    server,
                    pos,
                    facing.y,
                    facing.x,
                    dimType,
                    mode,
                    false
            );



            LOGGER.info("Spawned new bot {}!", botName);

            ServerPlayerEntity bot = server.getPlayerManager().getPlayer(botName);

            System.out.println("Preparing for connection to language model....");

            if (bot!=null) {

                Objects.requireNonNull(bot.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE)).setBaseValue(0.0);

                System.out.println("Registering respawn listener....");

                RespawnHandler.registerRespawnListener(bot);

                ollamaClient.botName = botName; // set the bot's name.

                System.out.println("Set bot's username to " + botName);

                String llmProvider = System.getProperty("aiplayer.llmMode", "ollama");

                System.out.println("Using provider");

                switch (llmProvider) {
                    case "openai", "gpt", "google", "gemini", "anthropic", "claude", "xAI", "xai", "grok", "custom":
                        LLMClient llmClient = LLMClientFactory.createClient(llmProvider);
                        assert llmClient != null;

                        ChatUtils.sendSystemMessage(serverSource, "Please wait while " + botName + " connects to " + llmClient.getProvider() + "'s servers.");
                        LLMServiceHandler.sendInitialResponse(bot.getCommandSource().withSilent().withMaxLevel(4), llmClient);

                        new Thread(() -> {
                            LOGGER.info("Waiting for LLM Service Handler to initialize...");
                            int waitCount = 0;
                            while (!LLMServiceHandler.isInitialized) {
                                try {
                                    Thread.sleep(500L); // Check every 500ms
                                    waitCount++;
                                    if (waitCount % 10 == 0) { // Log every 5 seconds
                                        LOGGER.warn("Still waiting for LLM initialization... ({} seconds)", waitCount / 2);
                                    }
                                    if (waitCount > 60) { // 30 second timeout
                                        LOGGER.error("LLM initialization timeout! Starting AutoFace anyway.");
                                        AutoFaceEntity.startAutoFace(bot);
                                        Thread.currentThread().interrupt();
                                        return;
                                    }
                                } catch (InterruptedException e) {
                                    LOGGER.error("LLM client initialization interrupted.");
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }

                            //initialization succeeded, continue:
                            LOGGER.info("LLM Service Handler initialized! Starting AutoFace...");
                            AutoFaceEntity.startAutoFace(bot);

                            Thread.currentThread().interrupt(); // close this thread.

                        }).start();

                        break;

                    case "ollama":
                        ChatUtils.sendSystemMessage(serverSource, "Please wait while " + botName + " connects to the language model.");
                        ollamaClient.initializeOllamaClient();

                        new Thread(() -> {
                            LOGGER.info("Waiting for Ollama client to initialize...");
                            int waitCount = 0;
                            while (!ollamaClient.isInitialized) {
                                try {
                                    Thread.sleep(500L); // Check every 500ms
                                    waitCount++;
                                    if (waitCount % 10 == 0) { // Log every 5 seconds
                                        LOGGER.warn("Still waiting for Ollama initialization... ({} seconds)", waitCount / 2);
                                    }
                                    if (waitCount > 60) { // 30 second timeout
                                        LOGGER.error("Ollama initialization timeout! Starting AutoFace anyway.");
                                        AutoFaceEntity.startAutoFace(bot);
                                        Thread.currentThread().interrupt();
                                        return;
                                    }
                                } catch (InterruptedException e) {
                                    LOGGER.error("Ollama client initialization interrupted.");
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }

                            //initialization succeeded, continue:
                            LOGGER.info("Ollama client initialized! Starting AutoFace...");
                            ollamaClient.sendInitialResponse(bot.getCommandSource().withSilent().withMaxLevel(4));
                            AutoFaceEntity.startAutoFace(bot);

                            Thread.currentThread().interrupt(); // close this thread.

                        }).start();

                        break;

                    default:
                        LOGGER.warn("Unsupported provider detected. Defaulting to Ollama client");
                        ChatUtils.sendSystemMessage(serverSource, "Warning! Unsupported provider detected. Defaulting to Ollama client");
                        ChatUtils.sendSystemMessage(serverSource, "Please wait while " + botName + " connects to the language model.");
                        ollamaClient.initializeOllamaClient();

                        new Thread(() -> {
                            while (!ollamaClient.isInitialized) {
                                try {
                                    Thread.sleep(500L); // Check every 500ms
                                } catch (InterruptedException e) {
                                    LOGGER.error("Ollama client initialization interrupted.");
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }

                            //initialization succeeded, continue:
                            ollamaClient.sendInitialResponse(bot.getCommandSource().withSilent().withMaxLevel(4));
                            AutoFaceEntity.startAutoFace(bot);

                            Thread.currentThread().interrupt(); // close this thread.

                        }).start();

                        break;

                }

            }


            else {
                ChatUtils.sendSystemMessage(serverSource, "Error: " + botName + " cannot be spawned");
            }

        }
        else {
            ChatUtils.sendSystemMessage(serverSource, "Invalid spawn mode!");
            ChatUtils.sendSystemMessage(serverSource, "Usage: /bot spawn <your bot's name> <spawnMode: training or play>");
        }


    }

    private static void notImplementedMessage(CommandContext<ServerCommandSource> context) {

        MinecraftServer server = context.getSource().getServer();

        String botName = StringArgumentType.getString(context, "bot_name");

        ServerPlayerEntity bot = server.getPlayerManager().getPlayer(botName);

        if (bot == null) {

            context.getSource().sendMessage(Text.of("The requested bot could not be found on the server!"));
            server.sendMessage(Text.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");

        }

        else {

            ServerCommandSource botSource = bot.getCommandSource().withLevel(2).withSilent().withMaxLevel(4);

            server.getCommandManager().executeWithPrefix(botSource, "/say §cThis command has not been implemented yet and is a work in progress! ");


        }


    }

    private static void teleportForward(CommandContext<ServerCommandSource> context) {
        MinecraftServer server = context.getSource().getServer();

        ServerPlayerEntity bot = null;
        try {bot = EntityArgumentType.getPlayer(context, "bot");} catch (CommandSyntaxException ignored) {}

        if (bot == null) {

            context.getSource().sendMessage(Text.of("The requested bot could not be found on the server!"));
            server.sendMessage(Text.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");

        }

        else {
            String botName = bot.getName().getLiteralString();

            BlockPos currentPosition = bot.getBlockPos();
            BlockPos newPosition = currentPosition.add(1, 0, 0); // Move one block forward
            bot.teleport(bot.getServerWorld(), newPosition.getX(), newPosition.getY(), newPosition.getZ(), Set.of(), bot.getYaw(), bot.getPitch());

            LOGGER.info("Teleported {} 1 positive block ahead", botName);

        }

    }

    private static void botWalk(CommandContext<ServerCommandSource> context) {

        MinecraftServer server = context.getSource().getServer();

        ServerPlayerEntity bot = null;
        try {bot = EntityArgumentType.getPlayer(context, "bot");} catch (CommandSyntaxException ignored) {}

        int travelTime = IntegerArgumentType.getInteger(context, "till");


        if (bot == null) {

            context.getSource().sendMessage(Text.of("The requested bot could not be found on the server!"));
            server.sendMessage(Text.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");

        }

        else {

            String botName = bot.getName().getLiteralString();

            ServerCommandSource botSource = bot.getCommandSource().withLevel(2).withSilent().withMaxLevel(4);
            moveForward(server, botSource, botName);

            scheduler.schedule(new BotStopTask(server, botSource, botName), travelTime, TimeUnit.SECONDS);


        }

    }


    private static void botJump(CommandContext<ServerCommandSource> context) {

        MinecraftServer server = context.getSource().getServer();

        ServerPlayerEntity bot = null;
        try {bot = EntityArgumentType.getPlayer(context, "bot");} catch (CommandSyntaxException ignored) {}


        if (bot == null) {

            context.getSource().sendMessage(Text.of("The requested bot could not be found on the server!"));
            server.sendMessage(Text.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");

        }

        else {

            String botName = bot.getName().getLiteralString();

            bot.jump();


            LOGGER.info("{} jumped!", botName);


        }

    }

    private static void testChatMessage(CommandContext<ServerCommandSource> context) {

        String response = "I am doing great! It feels good to be able to chat with you again after a long time. So, how have you been doing? Are you enjoying the game world and having fun playing Minecraft with me? Let's continue chatting about whatever topic comes to mind! I love hearing from you guys and seeing your creations in the game. Don't hesitate to share anything with me, whether it's an idea, a problem, or simply something that makes you laugh. Cheers!";

        MinecraftServer server = context.getSource().getServer();

        ServerPlayerEntity bot = null;
        try {bot = EntityArgumentType.getPlayer(context, "bot");} catch (CommandSyntaxException ignored) {}

        if (bot != null) {

            ServerCommandSource botSource = bot.getCommandSource().withMaxLevel(4).withSilent();
            ChatUtils.sendChatMessages(botSource, response);

        }
        else {
            context.getSource().sendMessage(Text.of("The requested bot could not be found on the server!"));
            server.sendMessage(Text.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");

        }

    }

    private static void botGo(CommandContext<ServerCommandSource> context) {
        MinecraftServer server = context.getSource().getServer();
        BlockPos position = BlockPosArgumentType.getBlockPos(context, "pos");
        String sprintFlag = StringArgumentType.getString(context, "sprint");

        boolean sprint;

        if (sprintFlag.equalsIgnoreCase("true")) {
            sprint = true;
        }
        else if (sprintFlag.equalsIgnoreCase("false")) {
            sprint = false;
        }
        else {
            sprint = false;
            ChatUtils.sendChatMessages(server.getCommandSource(), "Wrong argument! Command is as follows: /bot go_to <botName> <xyz> <true/false (case insensitive)>");
        }

        int x_distance = position.getX();
        int y_distance = position.getY();
        int z_distance = position.getZ();

        ServerWorld world = server.getOverworld();

        ServerPlayerEntity bot = null;
        try {
            bot = EntityArgumentType.getPlayer(context, "bot");
        } catch (CommandSyntaxException ignored) {}

        if (bot == null) {
            context.getSource().sendMessage(Text.of("The requested bot could not be found on the server!"));
            server.sendMessage(Text.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");
            return;  // stop here if no bot
        }

        String botName = bot.getName().getLiteralString();
        ServerCommandSource botSource = bot.getCommandSource().withLevel(2).withSilent().withMaxLevel(4);

        server.sendMessage(Text.literal("Finding the shortest path to the target, please wait patiently if the game seems hung"));

        ServerPlayerEntity finalBot = bot;

        server.execute(() -> {
            // ✅ Calculate the path (PathNode version)
            List<PathFinder.PathNode> rawPath = PathFinder.calculatePath(finalBot.getBlockPos(), new BlockPos(x_distance, y_distance, z_distance), world);

            // ✅ Simplify + filter
            List<PathFinder.PathNode> finalPath = PathFinder.simplifyPath(rawPath, world);

            LOGGER.info("Path output: {}", finalPath);

            Queue<Segment> segments = convertPathToSegments(finalPath, sprint);

            LOGGER.info("Generated segments: {}", segments);


            // ✅ Trace the path — your tracePath now expects PathNode
            PathTracer.tracePath(server, botSource, botName, segments, sprint);

        });
    }




    public static void moveForward(MinecraftServer server, ServerCommandSource source, String botName) {

        if (source.getPlayer() != null) {

            server.getCommandManager().executeWithPrefix(source, "/player " + botName + " move forward");

        }

    }

    private static void moveBackward(MinecraftServer server, ServerCommandSource source, String botName) {

        if (source.getPlayer() != null) {

            server.getCommandManager().executeWithPrefix(source, "/player " + botName + " move backward");

        }


    }

    public static void stopMoving(MinecraftServer server, ServerCommandSource source, String botName) {

        if (source.getPlayer() != null) {

            server.getCommandManager().executeWithPrefix(source, "/player " + botName + " stop");

        }


    }

    private static void moveLeft(MinecraftServer server, ServerCommandSource source, String botName) {

        if (source.getPlayer() != null) {

            server.getCommandManager().executeWithPrefix(source, "/player " + botName + " move left");

        }

    }

    private static void moveRight(MinecraftServer server, ServerCommandSource source, String botName) {

        if (source.getPlayer() != null) {

            server.getCommandManager().executeWithPrefix(source, "/player " + botName + " move right");

        }

    }


    private static @NotNull BlockPos getBlockPos(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer(); // gets the player who executed the command


        // Set spawn location for the second player
        assert player != null;
        return new BlockPos((int) player.getX() + 5, (int) player.getY(), (int) player.getZ());
    }

    /**
     * Intelligently selects the highest threat target from a list of hostile entities.
     * Uses the same risk calculation system as the bot's combat AI to prioritize targets.
     *
     * Priority examples:
     * - Creeper > Zombie (explosive threat)
     * - Skeleton > Spider (ranged threat)
     * - Close enemies > Far enemies (immediate danger)
     *
     * @param bot The bot selecting a target
     * @param hostileEntities List of nearby hostile entities
     * @param debugMode Whether to print debug messages
     * @param botSource Command source for chat messages
     * @return The highest threat entity, or null if none suitable
     */
    private static Entity selectHighestThreatTarget(ServerPlayerEntity bot, List<Entity> hostileEntities, boolean debugMode, ServerCommandSource botSource) {
        if (hostileEntities.isEmpty()) {
            return null;
        }

        // Calculate threat for each entity
        Entity highestThreatEntity = null;
        double highestThreat = -1.0;

        for (Entity entity : hostileEntities) {
            double distance = Math.sqrt(entity.squaredDistanceTo(bot));

            // Calculate base threat based on entity type (mobs or players)
            double baseThreat;
            if (entity instanceof net.minecraft.entity.player.PlayerEntity player) {
                // Use player-specific threat calculation (equipment-based)
                baseThreat = net.shasankp000.PlayerUtils.PlayerRetaliationTracker.getPlayerThreatLevel(bot, player);
            } else {
                // Use mob-specific threat calculation
                baseThreat = calculateBaseThreatForEntity(entity, distance);
            }

            // Apply distance modifier (closer = more dangerous, but consider ranged threats)
            // IMPROVED: Better balance between distance and threat type
            double distanceModifier = 0.0;
            String entityType = entity.getName().getString().toLowerCase();

            // For creepers and explosive threats, distance is CRITICAL
            if (entityType.contains("creeper")) {
                if (distance < 3.0) {
                    distanceModifier = 50.0; // CRITICAL - about to explode!
                } else if (distance < 5.0) {
                    distanceModifier = 30.0; // High danger zone
                } else if (distance < 8.0) {
                    distanceModifier = 10.0; // Still dangerous
                } else {
                    distanceModifier = -10.0; // Safer at range
                }
            }
            // For ranged attackers, medium distance is most dangerous
            else if (entityType.contains("skeleton") || entityType.contains("witch") ||
                     entityType.contains("blaze") || entityType.contains("pillager") ||
                     (entity instanceof net.minecraft.entity.player.PlayerEntity)) {
                if (distance < 3.0) {
                    distanceModifier = 5.0; // Close but manageable
                } else if (distance < 8.0) {
                    distanceModifier = 10.0; // Perfect shooting range for them
                } else if (distance < 15.0) {
                    distanceModifier = 5.0; // Still accurate
                } else if (distance < 25.0) {
                    distanceModifier = 0.0; // Snipe range - equal priority
                } else {
                    distanceModifier = -8.0; // Too far to be immediate threat
                }
            }
            // For melee threats, distance is inversely proportional to danger
            else {
                if (distance < 2.0) {
                    distanceModifier = 15.0; // Immediate danger!
                } else if (distance < 4.0) {
                    distanceModifier = 10.0; // Very close
                } else if (distance < 6.0) {
                    distanceModifier = 5.0; // Close
                } else if (distance < 10.0) {
                    distanceModifier = 0.0; // Medium range
                } else if (distance < 20.0) {
                    distanceModifier = -5.0; // Lower priority - can handle later
                } else {
                    distanceModifier = -10.0; // Very low priority
                }
            }

            double totalThreat = baseThreat + distanceModifier;

            if (debugMode) {
                String entityCategory = entity instanceof net.minecraft.entity.player.PlayerEntity ? "HOSTILE PLAYER" : "MOB";
                LOGGER.info("Target analysis: {} ({}) at {}m - Base: {}, Distance modifier: {}, Total: {}",
                    entity.getName().getString(),
                    entityCategory,
                    String.format("%.1f", distance),
                    String.format("%.1f", baseThreat),
                    String.format("%.1f", distanceModifier),
                    String.format("%.1f", totalThreat));
            }

            // Select highest threat
            if (totalThreat > highestThreat) {
                highestThreat = totalThreat;
                highestThreatEntity = entity;
            }
        }

        if (highestThreatEntity != null && debugMode) {
            String targetName = highestThreatEntity.getName().getString();
            double distance = Math.sqrt(highestThreatEntity.squaredDistanceTo(bot));

            ChatUtils.sendChatMessages(botSource,
                String.format("§c⚔ Priority Target: §e%s §7(Threat: §c%.1f§7, Distance: §e%.1fm§7)",
                    targetName, highestThreat, distance));

            // Explain why this target was chosen if there are multiple enemies
            if (hostileEntities.size() > 1) {
                String reason = getTargetSelectionReason(highestThreatEntity, distance);
                ChatUtils.sendChatMessages(botSource, "§7Reason: " + reason);
            }
        }

        return highestThreatEntity;
    }

    /**
     * Calculates base threat value for an entity based on type and distance.
     * Higher values = higher priority target.
     */
    private static double calculateBaseThreatForEntity(Entity entity, double distance) {
        String entityType = entity.getName().getString().toLowerCase();
        double baseThreat = 5.0; // Default threat

        // EXPLOSIVE THREATS - HIGHEST PRIORITY
        if (entityType.contains("creeper")) {
            baseThreat = 50.0;
            if (distance <= 3.0) baseThreat += 30.0; // Critical if in explosion range!
        }

        // MAXIMUM DANGER MOBS
        else if (entityType.contains("warden")) {
            baseThreat = 100.0; // Extreme threat
        }
        else if (entityType.contains("ravager")) {
            baseThreat = 40.0;
        }

        // RANGED ATTACKERS - HIGH PRIORITY
        else if (entityType.contains("skeleton") || entityType.contains("stray")) {
            baseThreat = 20.0;
        }
        else if (entityType.contains("witch")) {
            baseThreat = 25.0; // Potions are dangerous
        }
        else if (entityType.contains("blaze")) {
            baseThreat = 30.0;
        }
        else if (entityType.contains("ghast")) {
            baseThreat = 35.0;
        }
        else if (entityType.contains("drowned") && distance > 5.0) {
            baseThreat = 15.0; // Trident throw
        }
        else if (entityType.contains("pillager")) {
            baseThreat = 18.0;
        }

        // FLYING THREATS
        else if (entityType.contains("phantom")) {
            baseThreat = 22.0;
        }

        // MELEE THREATS
        else if (entityType.contains("zombie") || entityType.contains("husk")) {
            baseThreat = 8.0;
        }
        else if (entityType.contains("spider") || entityType.contains("cave_spider")) {
            baseThreat = 12.0;
        }
        else if (entityType.contains("enderman")) {
            baseThreat = 15.0;
        }
        else if (entityType.contains("vindicator")) {
            baseThreat = 25.0; // High damage
        }
        else if (entityType.contains("piglin")) {
            baseThreat = 10.0;
        }

        // SPECIAL CASES
        else if (entityType.contains("slime") || entityType.contains("magma_cube")) {
            baseThreat = 6.0;
        }
        else if (entityType.contains("silverfish")) {
            baseThreat = 4.0;
        }

        return baseThreat;
    }

    /**
     * Provides a human-readable explanation for why a target was selected.
     */
    private static String getTargetSelectionReason(Entity entity, double distance) {
        // Check if target is a hostile player
        if (entity instanceof net.minecraft.entity.player.PlayerEntity) {
            return "§4Hostile player - armed and dangerous!";
        }

        String entityType = entity.getName().getString().toLowerCase();

        // Explosive threats
        if (entityType.contains("creeper")) {
            return "§cExplosive threat - must eliminate immediately!";
        }

        // Ranged threats
        if (entityType.contains("skeleton") || entityType.contains("witch") ||
            entityType.contains("blaze") || entityType.contains("ghast")) {
            return "§6Ranged attacker - dangerous at distance";
        }

        // Flying threats
        if (entityType.contains("phantom")) {
            return "§bAerial threat - difficult to evade";
        }

        // Strong melee
        if (entityType.contains("warden") || entityType.contains("ravager")) {
            return "§4Extremely dangerous - maximum threat";
        }

        // Close proximity
        if (distance < 3.0) {
            return "§eImmediate danger - very close proximity";
        } else if (distance < 6.0) {
            return "§eClose range threat";
        }

        // Default
        return "§7Highest calculated threat";
    }

}
