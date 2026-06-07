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
import net.shasankp000.PathFinding.BotStance;
import net.shasankp000.PathFinding.ChartPathToBlock;
import net.shasankp000.PathFinding.GoTo;
import net.shasankp000.PathFinding.PathFinder;
import net.shasankp000.PathFinding.PathTracer;
import net.shasankp000.PathFinding.Segment;
import net.shasankp000.PathFinding.StanceController;
import net.shasankp000.PlayerUtils.*;
import net.shasankp000.ServiceLLMClients.LLMClient;
import net.shasankp000.ServiceLLMClients.LLMServiceHandler;
import net.shasankp000.WorldUitls.isFoodItem;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.shasankp000.GameAI.handoff.TradeEvaluator;
import net.shasankp000.GameAI.handoff.TradeListener;
import net.shasankp000.GameAI.mood.AffectiveState;
import net.shasankp000.GameAI.mood.MoodEngine;
import net.shasankp000.GameAI.mood.MoodLabel;
import net.shasankp000.GameAI.persona.PersonaRegistry;
import net.shasankp000.GameAI.persona.PersonaTemplate;
import java.util.stream.Collectors;

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

    // ---------------------------------------------------------------------------
    // Movement helpers used by PathTracer and other internal callers
    // ---------------------------------------------------------------------------

    /**
     * Issues a continuous-forward movement command to the bot via the
     * PlayerEx /player command.  Called by PathTracer to start each segment.
     */
    public static void moveForward(MinecraftServer server, ServerCommandSource botSource, String botName) {
        server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " move forward continuous");
    }


    public static void register() {
        // Register threat debug command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ThreatDebugCommand.register(dispatcher);
        });

        // Start the StanceController tick loop once at registration time.
        StanceController.start();

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
                        .then(literal("stance")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("mode", StringArgumentType.string())
                                                .then(CommandManager.argument("target", StringArgumentType.string())
                                                        .executes(context -> {
                                                            botStance(context, true);
                                                            return 1;
                                                        })
                                                )
                                                .executes(context -> {
                                                    botStance(context, false);
                                                    return 1;
                                                })
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

                                                    if (!RangedWeaponUtils.hasBowOrCrossbow(bot)) {
                                                        LOGGER.info("Bot does not have a bow or crossbow to shoot with.");
                                                        if (debugMode.equals("true")) {
                                                            ChatUtils.sendChatMessages(botSource, "I don't have a bow or crossbow!");
                                                        }
                                                        return 0;
                                                    }

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

                                                    List<Entity> nearbyEntities = AutoFaceEntity.detectNearbyEntities(bot, 40);
                                                    List<Entity> hostileEntities = nearbyEntities.stream()
                                                            .filter(entity -> {
                                                                if (entity instanceof HostileEntity) {
                                                                    return true;
                                                                }
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

                                                    AutoFaceEntity.isShooting = true;
                                                    LOGGER.info("Paused autoface for shooting");

                                                    int weaponSlot = RangedWeaponUtils.getBowOrCrossbowSlot(bot);
                                                    if (weaponSlot != -1 && bot.getInventory().selectedSlot != (weaponSlot - 1)) {
                                                        server.getCommandManager().executeWithPrefix(botSource, "/player " + bot.getName().getString() + " hotbar " + weaponSlot);
                                                        LOGGER.info("Switched to ranged weapon in slot {}", weaponSlot);
                                                        try {
                                                            Thread.sleep(100);
                                                        } catch (InterruptedException e) {
                                                            LOGGER.error("Weapon switch interrupted");
                                                        }
                                                    }

                                                    double projectileSpeed = ammoType.equals("firework") ? 1.5 : 3.0;
                                                    String ammoTypeName = ammoType.equals("firework") ? "firework rocket" : "arrow";

                                                    boolean isMovingFast = RangedWeaponUtils.isTargetMovingFast(target);

                                                    Vec3d aimPosition;
                                                    if (isMovingFast) {
                                                        aimPosition = RangedWeaponUtils.calculateLeadPosition(target, projectileSpeed);
                                                        LOGGER.info("Applied lead compensation for fast-moving target");
                                                    } else {
                                                        aimPosition = target.getPos().add(0, target.getHeight() * 0.6, 0);
                                                    }

                                                    float[] aimAngles = RangedWeaponUtils.calculateAimAngles(bot, aimPosition);
                                                    bot.setYaw(aimAngles[0]);
                                                    bot.setPitch(aimAngles[1]);

                                                    LOGGER.info("Aiming at {} at distance {} using {} (ballistic trajectory)",
                                                        targetName, String.format("%.1f", distance), ammoTypeName);

                                                    String weaponName = bot.getMainHandStack().getItem().getName().getString().toLowerCase();
                                                    boolean isCrossbow = weaponName.contains("crossbow");

                                                    int drawTime;
                                                    if (isCrossbow) {
                                                        drawTime = 25;
                                                    } else {
                                                        drawTime = net.shasankp000.PlayerUtils.WeaponUtils.calculateOptimalDrawTime(distance);
                                                        projectileSpeed = net.shasankp000.PlayerUtils.WeaponUtils.calculateProjectileSpeed(drawTime);
                                                        LOGGER.info("Dynamic bow draw time: {} ticks for {}m (speed: {})",
                                                            drawTime, String.format("%.1f", distance), String.format("%.2f", projectileSpeed));
                                                    }

                                                    AutoFaceEntity.setShootingTarget(target);

                                                    if (debugMode.equals("true")) {
                                                        ChatUtils.sendChatMessages(botSource, "Shooting target set to " + targetName);
                                                        ChatUtils.sendChatMessages(botSource, "Drawing " + (isCrossbow ? "crossbow" : "bow") + " with " + ammoTypeName + "...");
                                                    }

                                                    String playerName = bot.getName().getString();
                                                    server.getCommandManager().executeWithPrefix(botSource, "/player " + playerName + " use continuous");
                                                    LOGGER.info("Started drawing weapon");

                                                    Entity finalTarget = target;
                                                    String finalAmmoType = ammoType;
                                                    double finalProjectileSpeed = projectileSpeed;
                                                    scheduler.schedule(() -> {
                                                        if (finalTarget.isAlive()) {
                                                            Vec3d finalAimPosition;
                                                            if (isMovingFast) {
                                                                finalAimPosition = RangedWeaponUtils.calculateLeadPosition(finalTarget, finalProjectileSpeed);
                                                            } else {
                                                                finalAimPosition = finalTarget.getPos().add(0, finalTarget.getHeight() * 0.6, 0);
                                                            }
                                                            float[] reAimAngles = RangedWeaponUtils.calculateAimAngles(bot, finalAimPosition);
                                                            bot.setYaw(reAimAngles[0]);
                                                            bot.setPitch(reAimAngles[1]);
                                                        }

                                                        server.getCommandManager().executeWithPrefix(botSource, "/player " + playerName + " use");
                                                        if (debugMode.equals("true")) {
                                                            ChatUtils.sendChatMessages(botSource, ammoTypeName.substring(0, 1).toUpperCase() + ammoTypeName.substring(1) + " released at " + targetName + "!");
                                                        }
                                                        LOGGER.info("Shot {} at {} from {} blocks away", ammoTypeName, targetName, distance);

                                                        scheduler.schedule(() -> {
                                                            AutoFaceEntity.clearShootingTarget();
                                                            if (debugMode.equals("true")) {
                                                                ChatUtils.sendChatMessages(botSource, "Shot complete");
                                                            }
                                                            LOGGER.info("Shooting complete, autoface resumed");
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

                                                    net.shasankp000.GameAI.RLAgent rlAgent = BotEventHandler.getRLAgent();
                                                    if (rlAgent == null) {
                                                        LOGGER.error("RLAgent not initialized for bot {}", bot.getName().getString());
                                                        ChatUtils.sendChatMessages(botSource, "Error: AI system not ready");
                                                        return 0;
                                                    }

                                                    net.shasankp000.FunctionCaller.FunctionCallerV2.initializePlanner(bot, rlAgent);

                                                    net.shasankp000.GameAI.State currentState = BotEventHandler.getCurrentState();

                                                    ChatUtils.sendChatMessages(botSource, "Planning: " + goal + "...");

                                                    net.shasankp000.FunctionCaller.FunctionCallerV2.handleUserGoal(goal, currentState, bot, rlAgent, botSource)
                                                            .thenAccept(success -> {
                                                                server.execute(() -> {
                                                                    if (success) {
                                                                        ChatUtils.sendChatMessages(botSource, "\u2713 Plan executed successfully!");
                                                                        LOGGER.info("[planner] \u2713 Goal '{}' completed", goal);
                                                                    } else {
                                                                        ChatUtils.sendChatMessages(botSource, "\u2717 Plan execution failed");
                                                                        LOGGER.warn("[planner] \u2717 Goal '{}' failed", goal);
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

                                                                    int lavaRange = IntegerArgumentType.getInteger(context, "lavaRange");
                                                                    int cliffRange = IntegerArgumentType.getInteger(context, "cliffRange");
                                                                    int cliffDepth = IntegerArgumentType.getInteger(context, "cliffDepth");

                                                                    server.execute(() -> {
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

                                            StringBuilder messageBuilder = new StringBuilder();

                                            for (int i = 0; i < hotbarItems.size(); i++) {
                                                int slotIndex = i;

                                                ItemStack itemStack = hotbarItems.get(slotIndex);

                                                if (itemStack.isEmpty()) {
                                                    messageBuilder.append("Slot ").append(i+1).append(": EMPTY\n");
                                                } else {
                                                    messageBuilder.append("Slot ").append(i+1).append(": ")
                                                            .append(itemStack.getName().getString())
                                                            .append(" (Count: ").append(itemStack.getCount()).append(")\n");
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

                                    MinecraftServer server = context.getSource().getServer();
                                    ServerCommandSource serverSource = server.getCommandSource();

                                    ChatUtils.sendSystemMessage(serverSource, "Exporting Q-table to JSON. Please wait.... ");

                                    QTableExporter.exportQTable(BotEventHandler.qTableDir + "/qtable.bin", BotEventHandler.qTableDir + "./fullQTable.json");

                                    ChatUtils.sendSystemMessage(serverSource, "Q-table has been successfully exported to a json file at: " + BotEventHandler.qTableDir + "./fullQTable.json" );

                                    return 1;
                                })
                        )

                        // ── Feature 2: /bot mood ─────────────────────────────────────────────
                        // /bot mood <bot>              ->  show current mood snapshot
                        // /bot mood <bot> <mood_label> ->  override dominant mood
                        .then(literal("mood")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> {
                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                            String snapshot = MoodEngine.getStatusSnapshot(bot.getName().getString());
                                            ChatUtils.sendSystemMessage(context.getSource(),
                                                    "[" + bot.getName().getString() + "] mood: " + snapshot);
                                            return 1;
                                        })
                                        .then(CommandManager.argument("mood_label", StringArgumentType.string())
                                                .executes(context -> {
                                                    ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                                    String botName  = bot.getName().getString();
                                                    String labelStr = StringArgumentType.getString(context, "mood_label").toUpperCase();
                                                    MoodLabel label;
                                                    try {
                                                        label = MoodLabel.valueOf(labelStr);
                                                    } catch (IllegalArgumentException e) {
                                                        String valid = Arrays.stream(MoodLabel.values())
                                                                .map(Enum::name)
                                                                .collect(Collectors.joining(", "));
                                                        ChatUtils.sendSystemMessage(context.getSource(),
                                                                "Unknown mood label. Valid: " + valid);
                                                        return 0;
                                                    }
                                                    float valence = switch (label) {
                                                        case ELATED, CONTENT, SERENE -> 0.8f;
                                                        case EXCITED, CALM, NEUTRAL  -> 0.0f;
                                                        case AGITATED, BORED, DEPRESSED -> -0.8f;
                                                    };
                                                    float arousal = switch (label) {
                                                        case ELATED, EXCITED, AGITATED -> 0.9f;
                                                        case CONTENT, NEUTRAL, BORED   -> 0.5f;
                                                        case SERENE, CALM, DEPRESSED   -> 0.1f;
                                                    };
                                                    MoodEngine.set(botName, new AffectiveState(valence, arousal));
                                                    ChatUtils.sendSystemMessage(context.getSource(),
                                                            "[" + botName + "] mood set to: " + label.name());
                                                    return 1;
                                                })
                                        )
                                )
                        )

                        // ── Feature 3: /bot persona ──────────────────────────────────────────
                        // /bot persona <bot>              ->  list available personas + show active
                        // /bot persona <bot> <persona_id> ->  set persona
                        .then(literal("persona")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> {
                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                            String botName = bot.getName().getString();
                                            String all = PersonaRegistry.ids().stream()
                                                    .collect(Collectors.joining(", "));
                                            ChatUtils.sendSystemMessage(context.getSource(),
                                                    "[" + botName + "] available personas: " + all);
                                            return 1;
                                        })
                                        .then(CommandManager.argument("persona_id", StringArgumentType.string())
                                                .executes(context -> {
                                                    ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                                    String botName   = bot.getName().getString();
                                                    String personaId = StringArgumentType.getString(context, "persona_id");
                                                    Optional<PersonaTemplate> opt = PersonaRegistry.get(personaId);
                                                    if (opt.isEmpty()) {
                                                        String all = PersonaRegistry.ids().stream()
                                                                .collect(Collectors.joining(", "));
                                                        ChatUtils.sendSystemMessage(context.getSource(),
                                                                "Unknown persona id. Valid: " + all);
                                                        return 0;
                                                    }
                                                    PersonaTemplate template = opt.get();
                                                    PersonaRegistry.setActive(botName, personaId);
                                                    ChatUtils.sendSystemMessage(context.getSource(),
                                                            "[" + botName + "] persona set to: "
                                                            + template.displayName());
                                                    return 1;
                                                })
                                        )
                                )
                        )

                        // ── Feature 4: /bot trade ────────────────────────────────────────────
                        // /bot trade <bot>         ->  bot announces what it can offer
                        // /bot trade <bot> cancel  ->  cancel the caller's pending session
                        .then(literal("trade")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> {
                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                            ServerCommandSource src = context.getSource();
                                            ServerPlayerEntity player;
                                            try {
                                                player = src.getPlayerOrThrow();
                                            } catch (CommandSyntaxException e) {
                                                ChatUtils.sendSystemMessage(src, "This command must be run by a player.");
                                                return 0;
                                            }
                                            ItemStack botOffer = TradeEvaluator.evaluate(ItemStack.EMPTY, bot);
                                            if (botOffer.isEmpty()) {
                                                player.sendMessage(
                                                        Text.literal("\u00a7e[" + bot.getName().getString()
                                                                + "] \u00a7fI have nothing worth trading right now."),
                                                        false);
                                                return 0;
                                            }
                                            player.sendMessage(
                                                    Text.literal("\u00a7e[" + bot.getName().getString()
                                                            + "] \u00a7fI could offer: \u00a7b"
                                                            + TradeEvaluator.displayName(botOffer)
                                                            + "\u00a7f. Sneak and throw the item you want to give me!"),
                                                    false);
                                            return 1;
                                        })
                                        .then(literal("cancel")
                                                .executes(context -> {
                                                    ServerCommandSource src = context.getSource();
                                                    ServerPlayerEntity player;
                                                    try {
                                                        player = src.getPlayerOrThrow();
                                                    } catch (CommandSyntaxException e) {
                                                        ChatUtils.sendSystemMessage(src, "Must be run by a player.");
                                                        return 0;
                                                    }
                                                    TradeListener.cancelSession(player.getUuid());
                                                    ChatUtils.sendSystemMessage(src, "Trade session cancelled.");
                                                    return 1;
                                                })
                                        )
                                )
                        )

                        .then(literal("stopAllMovementTasks")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> {

                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                            MinecraftServer server = bot.getServer();
                                            assert server != null;

                                            ServerCommandSource botSource = bot.getCommandSource().withSilent().withMaxLevel(4);

                                            stopMoving(server, botSource, bot.getName().getString());

                                            return 1;
                                        })
                                )

                        )

        ));

    }

    private static void spawnBot(@NotNull CommandContext<ServerCommandSource> context, String spawnMode) {

        MinecraftServer server = context.getSource().getServer();
        ServerCommandSource serverSource = server.getCommandSource();

        String botName = StringArgumentType.getString(context, "bot_name");

        if (spawnMode.equals("survival")) {

            server.getCommandManager().executeWithPrefix(serverSource, "/player " + botName + " spawn");

        }

        else if (spawnMode.equals("creative")) {

            server.getCommandManager().executeWithPrefix(serverSource, "/player " + botName + " spawn");

        }

        else {
            ChatUtils.sendSystemMessage(serverSource, "Invalid mode: " + spawnMode + "! Valid modes are: survival, creative.");
        }

    }


    private static void botWalk(@NotNull CommandContext<ServerCommandSource> context) {

        MinecraftServer server = context.getSource().getServer();
        ServerCommandSource serverSource = server.getCommandSource();

        try {
            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
            int till = IntegerArgumentType.getInteger(context, "till");

            ServerCommandSource botSource = bot.getCommandSource().withSilent().withMaxLevel(4);

            botName = bot.getName().getString();

            // Issue continuous-forward movement command; a scheduled stop will cancel it.
            moveForward(server, botSource, botName);

            scheduler.schedule(new BotStopTask(server, botSource, botName), till * 1000L, TimeUnit.MILLISECONDS);

        } catch (CommandSyntaxException e) {
            LOGGER.error("Failed to get entity argument: " + e);
        }
    }

    private static void botJump(@NotNull CommandContext<ServerCommandSource> context) {
        MinecraftServer server = context.getSource().getServer();

        try {
            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");

            ServerCommandSource botSource = bot.getCommandSource().withSilent().withMaxLevel(4);

            botName = bot.getName().getString();

            server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " jump");

        } catch (CommandSyntaxException e) {
            LOGGER.error("Failed to get entity argument: " + e);
        }
    }

    private static void teleportForward(@NotNull CommandContext<ServerCommandSource> context) {
        MinecraftServer server = context.getSource().getServer();

        try {
            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
            ServerCommandSource botSource = bot.getCommandSource().withSilent().withMaxLevel(4);

            botName = bot.getName().getString();

            Vec3d currentPos = bot.getPos();
            Vec2f rotation = bot.getRotationClient();

            double yawRadians = Math.toRadians(rotation.y);

            double newX = currentPos.x - Math.sin(yawRadians) * 2;
            double newZ = currentPos.z + Math.cos(yawRadians) * 2;

            server.getCommandManager().executeWithPrefix(botSource, "/tp " + botName + " " + newX + " " + currentPos.y + " " + newZ);

        } catch (CommandSyntaxException e) {
            LOGGER.error("Failed to get entity argument: " + e);
        }
    }

    private static void testChatMessage(@NotNull CommandContext<ServerCommandSource> context) {

        MinecraftServer server = context.getSource().getServer();

        try {
            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");

            ServerCommandSource botSource = bot.getCommandSource().withSilent().withMaxLevel(4);

            ChatUtils.sendChatMessages(botSource, "Hello World!");

        } catch (CommandSyntaxException e) {
            LOGGER.error("Failed to get entity argument: " + e);
        }

    }

    private static void botGo(@NotNull CommandContext<ServerCommandSource> context) {

        MinecraftServer server = context.getSource().getServer();

        try {
            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
            ServerCommandSource botSource = bot.getCommandSource().withSilent().withMaxLevel(4);
            BlockPos targetPos = BlockPosArgumentType.getBlockPos(context, "pos");
            String sprint = StringArgumentType.getString(context, "sprint");
            botName = bot.getName().getString();
            boolean shouldSprint = sprint.equals("true");

            // GoTo.goTo() is the only navigation entry-point; run on a virtual thread
            // so the command dispatcher thread is not blocked.
            final boolean sprintFinal = shouldSprint;
            Thread.ofVirtual().name("bot-go-" + botName).start(() -> {
                try {
                    GoTo.goTo(botSource, targetPos.getX(), targetPos.getY(), targetPos.getZ(), sprintFinal);
                } catch (Exception e) {
                    LOGGER.error("[botGo] Navigation failed for '{}': {}", botName, e.getMessage());
                }
            });

        } catch (CommandSyntaxException e) {
            LOGGER.error("Failed to get entity argument: " + e);
        }

    }

    /**
     * Handles /bot stance <bot> <mode> [target].
     *
     * <p>BotStance was redesigned: it no longer uses AGGRESSIVE/DEFENSIVE/PASSIVE
     * enum constants.  The supported modes now map to the spatial behaviours
     * exposed by {@link BotStance}: STAY (anchor at current position) and
     * FOLLOW (follow a named player).  NONE / "clear" releases any active stance.
     *
     * <pre>
     *   /bot stance <bot> stay          – anchor to current position
     *   /bot stance <bot> follow <name> – follow player <name>
     *   /bot stance <bot> none           – clear stance
     * </pre>
     */
    private static void botStance(@NotNull CommandContext<ServerCommandSource> context, boolean hasTarget) {
        try {
            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
            String mode   = StringArgumentType.getString(context, "mode").toLowerCase();
            String target  = hasTarget ? StringArgumentType.getString(context, "target") : null;
            String botName = bot.getName().getString();

            switch (mode) {
                case "stay" -> {
                    BotStance.setStay(botName, bot.getBlockPos());
                    ChatUtils.sendSystemMessage(context.getSource(),
                            "[" + botName + "] stance set to STAY at " + bot.getBlockPos());
                }
                case "follow" -> {
                    if (target == null || target.isBlank()) {
                        ChatUtils.sendSystemMessage(context.getSource(),
                                "FOLLOW stance requires a target player name.");
                        return;
                    }
                    BotStance.setFollow(botName, target);
                    ChatUtils.sendSystemMessage(context.getSource(),
                            "[" + botName + "] stance set to FOLLOW targeting '" + target + "'");
                }
                case "none", "clear" -> {
                    StanceController.cancelStance(botName);
                    ChatUtils.sendSystemMessage(context.getSource(),
                            "[" + botName + "] stance cleared.");
                }
                default -> ChatUtils.sendSystemMessage(context.getSource(),
                        "Invalid stance mode '" + mode + "'. Use: stay, follow, none");
            }
        } catch (CommandSyntaxException e) {
            LOGGER.error("Failed to get entity argument: {}", e.getMessage());
        }
    }

    public static void stopMoving(MinecraftServer server, ServerCommandSource botSource, String botName) {

        server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " stop");

    }

    private static Entity selectHighestThreatTarget(ServerPlayerEntity bot, List<Entity> hostileEntities,
                                                     boolean debugMode, ServerCommandSource botSource) {
        if (hostileEntities.isEmpty()) return null;
        if (hostileEntities.size() == 1) return hostileEntities.get(0);

        Entity highestThreatTarget = null;
        double highestThreatScore = -1;

        for (Entity entity : hostileEntities) {
            double threatScore = 0;

            // Base distance threat (closer = higher threat)
            double distance = bot.getPos().distanceTo(entity.getPos());
            threatScore += Math.max(0, 40 - distance) / 40.0 * 50;

            // Entity type threat multiplier
            if (entity instanceof HostileEntity hostile) {
                // Health factor (lower health = easier to kill = higher priority)
                double healthRatio = hostile.getHealth() / hostile.getMaxHealth();
                threatScore += (1 - healthRatio) * 20;

                // Armor factor — EntityAttributes.GENERIC_ARMOR is the 1.21.1 registry key
                double armorValue = hostile.getAttributeValue(EntityAttributes.GENERIC_ARMOR);
                threatScore -= armorValue * 0.5;
            }

            if (debugMode && botSource != null) {
                ChatUtils.sendChatMessages(botSource,
                    "Entity: " + entity.getName().getString() + " | Threat Score: " + String.format("%.1f", threatScore));
            }

            if (threatScore > highestThreatScore) {
                highestThreatScore = threatScore;
                highestThreatTarget = entity;
            }
        }

        return highestThreatTarget;
    }

}
