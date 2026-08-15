package net.shasankp000.PathFinding;

import carpet.fakes.ServerPlayerInterface;
import carpet.helpers.EntityPlayerActionPack;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.shasankp000.PlayerUtils.FoodConsumptionTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Owns all server-authoritative, per-bot navigation sessions. */
public final class NavigationService {
    private static final Logger LOGGER = LoggerFactory.getLogger("ai-player");
    private static final Map<UUID, NavigationSession> SESSIONS = new ConcurrentHashMap<>();
    private static final AtomicLong GENERATIONS = new AtomicLong();
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final int STALL_WINDOW_TICKS = 20;
    private static final double MIN_WINDOW_PROGRESS = 0.05;
    private static final int MAX_RECOVERIES = 3;

    private NavigationService() {}

    public static void register() {
        if (REGISTERED.compareAndSet(false, true)) {
            ServerTickEvents.END_SERVER_TICK.register(NavigationService::tick);
        }
    }

    public static CompletableFuture<NavigationResult> navigate(ServerPlayer player, BlockPos goal,
                                                               NavigationOptions options) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(goal, "goal");
        Objects.requireNonNull(options, "options");
        MinecraftServer server = player.level().getServer();
        CompletableFuture<NavigationResult> future = new CompletableFuture<>();
        Runnable begin = () -> beginOnServer(player, goal.immutable(), options, future);
        if (server == null) {
            future.complete(new NavigationResult(NavigationResult.Status.PLAYER_UNAVAILABLE,
                    player.blockPosition(), "Server unavailable"));
        } else if (server.isSameThread()) {
            begin.run();
        } else {
            server.execute(begin);
        }
        return future;
    }

    public static boolean isNavigating(UUID botId) {
        return SESSIONS.containsKey(botId);
    }

    public static boolean isAnyNavigating() {
        return !SESSIONS.isEmpty();
    }

    public static void cancel(UUID botId, String reason) {
        NavigationSession session = SESSIONS.get(botId);
        if (session != null) cancel(session.server, botId, reason);
    }

    public static void cancel(MinecraftServer server, UUID botId, String reason) {
        Runnable cancel = () -> {
            NavigationSession session = SESSIONS.get(botId);
            if (session != null) finish(session, NavigationResult.Status.CANCELLED, reason);
        };
        if (server.isSameThread()) cancel.run(); else server.execute(cancel);
    }

    public static void cancelAll(MinecraftServer server, String reason) {
        Runnable cancel = () -> new ArrayList<>(SESSIONS.values())
                .forEach(session -> finish(session, NavigationResult.Status.CANCELLED, reason));
        if (server.isSameThread()) cancel.run(); else server.execute(cancel);
    }

    public static void cancelAll(String reason) {
        Set<MinecraftServer> servers = new HashSet<>();
        for (NavigationSession session : SESSIONS.values()) servers.add(session.server);
        for (MinecraftServer server : servers) cancelAll(server, reason);
    }

    private static void beginOnServer(ServerPlayer player, BlockPos goal, NavigationOptions options,
                                      CompletableFuture<NavigationResult> future) {
        NavigationSession previous = SESSIONS.get(player.getUUID());
        if (previous != null) finish(previous, NavigationResult.Status.CANCELLED, "Replaced by a newer route");
        if (!player.isAlive() || player.hasDisconnected()) {
            future.complete(new NavigationResult(NavigationResult.Status.PLAYER_UNAVAILABLE,
                    player.blockPosition(), "Player is unavailable"));
            return;
        }
        NavigationSession session = new NavigationSession(player, goal, options,
                GENERATIONS.incrementAndGet(), future);
        SESSIONS.put(player.getUUID(), session);
        startPlanning(session, player);
    }

    private static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        for (NavigationSession session : new ArrayList<>(SESSIONS.values())) {
            if (SESSIONS.get(session.botId) != session) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(session.botId);
            if (player == null || !player.isAlive() || player.hasDisconnected()) {
                finish(session, NavigationResult.Status.PLAYER_UNAVAILABLE, "Player became unavailable");
                continue;
            }
            if (player.level().dimension() != session.dimension) {
                finish(session, NavigationResult.Status.CANCELLED, "Player changed dimension");
                continue;
            }
            if (FoodConsumptionTool.isConsumptionInProgress(session.botId)) {
                session.resetProgressWindow(player.position());
                continue;
            }
            session.lastPosition = player.blockPosition();
            if (session.search != null) tickPlanning(session, player);
            else tickMovement(session, player);
        }
    }

    private static void tickPlanning(NavigationSession session, ServerPlayer player) {
        PathFinder.SearchStatus status = session.search.advance(session.options.expansionsPerTick());
        if (status == PathFinder.SearchStatus.SEARCHING) return;
        if (status == PathFinder.SearchStatus.INVALID_GOAL) {
            finish(session, NavigationResult.Status.INVALID_GOAL, "No safe standing or swimming position near the goal");
            return;
        }
        if (status == PathFinder.SearchStatus.NO_PATH) {
            finish(session, NavigationResult.Status.NO_PATH, "No collision-safe route through loaded terrain");
            return;
        }
        session.path = session.search.result();
        session.partialPath = session.search.isPartial();
        session.search = null;
        if (session.path.size() < 2) {
            if (atFinalGoal(player.position(), session.goal)) {
                finish(session, NavigationResult.Status.REACHED, "Already at destination");
            } else if (session.partialPath) {
                startPlanning(session, player);
            } else {
                finish(session, NavigationResult.Status.NO_PATH, "Planner returned no traversable edges");
            }
            return;
        }
        session.waypointIndex = 1;
        session.cornerPauseTicks = 0;
        session.resetProgressWindow(player.position());
        LOGGER.debug("Navigation {} planned {} waypoints for {}", session.generation,
                session.path.size(), player.getGameProfile().name());
    }

    private static void tickMovement(NavigationSession session, ServerPlayer player) {
        if (session.path.isEmpty() || session.waypointIndex >= session.path.size()) {
            if (session.partialPath && !atFinalGoal(player.position(), session.goal)) startPlanning(session, player);
            else finish(session, NavigationResult.Status.REACHED, "Destination reached");
            return;
        }
        if (!(player instanceof ServerPlayerInterface playerInterface)) {
            finish(session, NavigationResult.Status.PLAYER_UNAVAILABLE, "Carpet action pack is unavailable");
            return;
        }
        EntityPlayerActionPack actions = playerInterface.getActionPack();
        PathFinder.PathNode waypoint = session.path.get(session.waypointIndex);
        PathFinder.PathNode previous = session.path.get(session.waypointIndex - 1);

        boolean expectedAir = waypoint.movement() == MovementType.JUMP_UP
                || waypoint.movement() == MovementType.DROP || waypoint.movement().isWaterMovement();
        if (!expectedAir && !player.onGround() && !player.isInWater() && player.getDeltaMovement().y < -0.08) {
            session.replanAfterFall = true;
        }
        if (session.replanAfterFall && player.onGround()) {
            stopNavigationInputs(actions);
            session.replanAfterFall = false;
            startPlanning(session, player);
            return;
        }

        if (++session.validationTicks >= 10) {
            session.validationTicks = 0;
            if (!PathFinder.isWaypointStillValid(player.level(), waypoint)) {
                stopNavigationInputs(actions);
                startPlanning(session, player);
                return;
            }
        }

        if (reachedWaypoint(player, previous.target(), waypoint)) {
            stopNavigationInputs(actions);
            session.waypointIndex++;
            session.cornerPauseTicks = 1;
            session.recoveries = 0;
            session.resetProgressWindow(player.position());
            return;
        }

        if (session.cornerPauseTicks > 0) {
            stopNavigationInputs(actions);
            session.cornerPauseTicks--;
            return;
        }

        double deviation = distanceFromSegment(player.position(), previous.target(), waypoint.target());
        if (deviation > 2.5) {
            stopNavigationInputs(actions);
            startPlanning(session, player);
            return;
        }

        if (session.recoveryTicks > 0) {
            actions.setSprinting(false).setForward(-0.35F)
                    .setStrafing(session.recoveries % 2 == 0 ? 0.7F : -0.7F);
            session.recoveryTicks--;
            if (session.recoveryTicks == 0) {
                stopNavigationInputs(actions);
                startPlanning(session, player);
            }
            return;
        }

        steer(actions, player, waypoint, session);
        updateProgress(session, player, actions, waypoint);
    }

    private static void steer(EntityPlayerActionPack actions, ServerPlayer player,
                              PathFinder.PathNode waypoint, NavigationSession session) {
        Vec3 target = waypoint.target();
        double dx = target.x - player.getX();
        double dz = target.z - player.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = 0.0F;
        if (waypoint.movement().isWaterMovement()) {
            double horizontal = Math.max(0.01, Math.hypot(dx, dz));
            pitch = (float) -Math.toDegrees(Math.atan2(target.y - player.getY(), horizontal));
            pitch = Math.max(-35.0F, Math.min(35.0F, pitch));
        }
        actions.look(yaw, pitch).setStrafing(0.0F).setForward(1.0F);

        double distance = Math.hypot(dx, dz);
        boolean actionBoundary = waypoint.movement().isActionBoundary();
        boolean canSprint = session.options.sprint() && !actionBoundary && distance > 1.5
                && !player.isInWater() && player.getFoodData().getFoodLevel() > 6;
        actions.setSprinting(canSprint);

        if (waypoint.movement() == MovementType.JUMP_UP && player.onGround() && distance <= 1.15
                && session.jumpCooldown == 0) {
            actions.start(EntityPlayerActionPack.ActionType.JUMP, EntityPlayerActionPack.Action.once());
            session.jumpCooldown = 6;
        } else if (waypoint.movement() == MovementType.SWIM && target.y > player.getY() + 0.2) {
            actions.start(EntityPlayerActionPack.ActionType.JUMP, EntityPlayerActionPack.Action.continuous());
        } else if (session.jumpCooldown > 0) {
            session.jumpCooldown--;
        } else {
            actions.start(EntityPlayerActionPack.ActionType.JUMP, null);
        }
    }

    private static void updateProgress(NavigationSession session, ServerPlayer player,
                                       EntityPlayerActionPack actions, PathFinder.PathNode waypoint) {
        double remaining = player.position().distanceTo(waypoint.target());
        if (session.progressTicks == 0) session.windowStartRemaining = remaining;
        session.progressTicks++;
        if (session.progressTicks < STALL_WINDOW_TICKS) return;
        double progress = session.windowStartRemaining - remaining;
        session.progressTicks = 0;
        session.windowStartRemaining = remaining;
        if (progress >= MIN_WINDOW_PROGRESS || (!player.onGround() && !player.isInWater())) return;
        stopNavigationInputs(actions);
        session.recoveries++;
        session.penalizedTargets.add(waypoint.getPos().immutable());
        if (session.recoveries > MAX_RECOVERIES) {
            finish(session, NavigationResult.Status.STUCK, "No movement progress after recovery attempts");
        } else {
            session.recoveryTicks = 6;
        }
    }

    private static boolean reachedWaypoint(ServerPlayer player, Vec3 from, PathFinder.PathNode waypoint) {
        Vec3 target = waypoint.target();
        double horizontal = Math.hypot(player.getX() - target.x, player.getZ() - target.z);
        double vertical = Math.abs(player.getY() - target.y);
        if (waypoint.movement().targetIsWater()) return player.position().distanceTo(target) <= 0.75;
        boolean settled = waypoint.movement() != MovementType.JUMP_UP && waypoint.movement() != MovementType.DROP
                || player.onGround();
        if (horizontal <= 0.35 && vertical <= 0.65 && settled) return true;
        Vec3 edge = target.subtract(from);
        double lengthSquared = edge.x * edge.x + edge.z * edge.z;
        if (lengthSquared < 1.0E-6) return false;
        Vec3 offset = player.position().subtract(from);
        double projection = (offset.x * edge.x + offset.z * edge.z) / lengthSquared;
        return projection >= 1.0 && distanceFromSegment(player.position(), from, target) <= 0.65
                && vertical <= 0.75 && settled;
    }

    static double distanceFromSegment(Vec3 point, Vec3 start, Vec3 end) {
        double dx = end.x - start.x;
        double dz = end.z - start.z;
        double lengthSquared = dx * dx + dz * dz;
        if (lengthSquared < 1.0E-9) return Math.hypot(point.x - start.x, point.z - start.z);
        double t = ((point.x - start.x) * dx + (point.z - start.z) * dz) / lengthSquared;
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(point.x - (start.x + dx * t), point.z - (start.z + dz * t));
    }

    private static boolean atFinalGoal(Vec3 position, BlockPos goal) {
        Vec3 target = Vec3.atBottomCenterOf(goal);
        return Math.hypot(position.x - target.x, position.z - target.z) <= 0.35
                && Math.abs(position.y - target.y) <= 0.75;
    }

    private static void startPlanning(NavigationSession session, ServerPlayer player) {
        session.path = List.of();
        session.waypointIndex = 0;
        session.search = PathFinder.beginSearch(player.level(), player.position(), session.goal,
                session.options.planningHorizon(), session.penalizedTargets);
        session.resetProgressWindow(player.position());
    }

    private static void finish(NavigationSession session, NavigationResult.Status status, String message) {
        if (!SESSIONS.remove(session.botId, session)) return;
        MinecraftServer server = session.server;
        ServerPlayer player = server.getPlayerList().getPlayer(session.botId);
        BlockPos finalPosition = player != null ? player.blockPosition() : session.lastPosition;
        if (player instanceof ServerPlayerInterface playerInterface) stopNavigationInputs(playerInterface.getActionPack());
        session.future.complete(new NavigationResult(status, finalPosition, message));
        LOGGER.debug("Navigation {} completed for {}: {} ({})", session.generation, session.botId, status, message);
    }

    private static void stopNavigationInputs(EntityPlayerActionPack actions) {
        actions.setForward(0.0F).setStrafing(0.0F).setSprinting(false)
                .start(EntityPlayerActionPack.ActionType.JUMP, null);
    }

    private static final class NavigationSession {
        final UUID botId;
        final MinecraftServer server;
        final ResourceKey<Level> dimension;
        final BlockPos goal;
        final NavigationOptions options;
        final long generation;
        final CompletableFuture<NavigationResult> future;
        PathFinder.Search search;
        List<PathFinder.PathNode> path = List.of();
        int waypointIndex;
        int progressTicks;
        int validationTicks;
        int cornerPauseTicks;
        int jumpCooldown;
        int recoveries;
        int recoveryTicks;
        double windowStartRemaining;
        boolean partialPath;
        boolean replanAfterFall;
        final Set<BlockPos> penalizedTargets = new HashSet<>();
        BlockPos lastPosition;

        NavigationSession(ServerPlayer player, BlockPos goal, NavigationOptions options, long generation,
                          CompletableFuture<NavigationResult> future) {
            this.botId = player.getUUID();
            this.server = Objects.requireNonNull(player.level().getServer());
            this.dimension = player.level().dimension();
            this.goal = goal;
            this.options = options;
            this.generation = generation;
            this.future = future;
            this.lastPosition = player.blockPosition();
        }

        void resetProgressWindow(Vec3 position) {
            progressTicks = 0;
            windowStartRemaining = 0;
            lastPosition = BlockPos.containing(position);
        }
    }
}
