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
import net.shasankp000.Mood.MoodEngine;
import net.shasankp000.Mood.MoodLabel;
import net.shasankp000.OllamaClient.ollamaClient;
import net.shasankp000.PathFinding.BotStance;
import net.shasankp000.PathFinding.ChartPathToBlock;
import net.shasankp000.PathFinding.GoTo;
import net.shasankp000.PathFinding.PathFinder;
import net.shasankp000.PathFinding.PathTracer;
import net.shasankp000.PathFinding.Segment;
import net.shasankp000.PathFinding.StanceController;
import net.shasankp000.Persona.PersonaRegistry;
import net.shasankp000.Persona.PersonaTemplate;
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
import java.util.stream.Collectors;


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
                        // ----------------------------------------------------------------
                        // /bot stance <bot> <stay|follow|cancel> [targetPlayerName]
                        //
                        // stay   — bot holds its current position; auto-corrects if pushed
                        // follow — bot shadows <targetPlayerName>; re-paths when they move
                        // cancel — removes any active stance and stops ongoing path
                        // ----------------------------------------------------------------
                        .then(literal("stance")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("mode", StringArgumentType.string())
                                                // variant with optional target player name (for follow)
                                                .then(CommandManager.argument("target", StringArgumentType.string())
                                                        .executes(context -> {
                                                            botStance(context, true);
                                                            return 1;
                                                        })
                                                )
                                                // variant without target (stay / cancel)
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

                                                    // Intelligent targeting: use risk analysis to prioritize threats
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
                                                    String goal 