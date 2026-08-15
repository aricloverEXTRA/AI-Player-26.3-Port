package net.shasankp000.PathFinding;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/** Directional, collision-aware A* for a player-sized server-side bot. */
public final class PathFinder {
    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");
    private static final double HALF_WIDTH = 0.29;
    private static final double HEIGHT = 1.79;
    private static final double EPSILON = 1.0E-4;
    private static final int MAX_EXPANSIONS = 100_000;
    private static final int[][] HORIZONTAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private PathFinder() {}

    public static final class PathNode {
        public final BlockPos pos;
        public final String blockName;
        public final boolean jump;
        public final boolean walkable;
        private final Vec3 target;
        private final MovementType movement;

        public PathNode(BlockPos pos, String blockName, boolean walkable, boolean jump) {
            this(Vec3.atBottomCenterOf(pos), jump ? MovementType.JUMP_UP : MovementType.WALK,
                    blockName, walkable);
        }

        public PathNode(Vec3 target, MovementType movement, String blockName, boolean walkable) {
            this.target = target;
            this.pos = BlockPos.containing(target.x, target.y + EPSILON, target.z);
            this.movement = movement;
            this.blockName = blockName;
            this.jump = movement == MovementType.JUMP_UP;
            this.walkable = walkable;
        }

        public boolean jumpNeeded() { return jump; }
        public BlockPos getPos() { return pos; }
        public Vec3 target() { return target; }
        public MovementType movement() { return movement; }

        @Override
        public String toString() { return "PathNode{target=" + target + ", movement=" + movement + '}'; }
    }

    private record NavKey(int x, int y, int z, boolean water) {}

    private static final class SearchNode implements Comparable<SearchNode> {
        final NavKey key;
        final Vec3 target;
        final MovementType movement;
        final SearchNode parent;
        final double g;
        final double h;

        SearchNode(NavKey key, Vec3 target, MovementType movement, SearchNode parent, double g, double h) {
            this.key = key;
            this.target = target;
            this.movement = movement;
            this.parent = parent;
            this.g = g;
            this.h = h;
        }

        @Override public int compareTo(SearchNode other) { return Double.compare(g + h, other.g + other.h); }
    }

    public enum SearchStatus { SEARCHING, FOUND, NO_PATH, INVALID_GOAL }

    /** Stateful search so callers can budget expansions over server ticks. */
    public static final class Search {
        private final ServerLevel world;
        private final PriorityQueue<SearchNode> open = new PriorityQueue<>();
        private final Map<NavKey, SearchNode> openByKey = new HashMap<>();
        private final Set<NavKey> closed = new HashSet<>();
        private final Vec3 requestedGoal;
        private final Vec3 effectiveGoal;
        private final Set<BlockPos> penalizedTargets;
        private SearchStatus status = SearchStatus.SEARCHING;
        private List<PathNode> result = List.of();
        private int expansions;

        private Search(ServerLevel world, Vec3 startPosition, BlockPos goal, int horizon,
                       Set<BlockPos> penalizedTargets) {
            this.world = world;
            this.penalizedTargets = Set.copyOf(penalizedTargets);
            this.requestedGoal = Vec3.atBottomCenterOf(goal);
            Vec3 localGoal = capToLoadedHorizon(world, startPosition, requestedGoal, horizon);
            Optional<SearchNode> start = normalizeStart(world, startPosition);
            Optional<SearchNode> normalizedGoal = normalizeGoal(world, BlockPos.containing(localGoal));
            if (start.isEmpty() || normalizedGoal.isEmpty()) {
                status = SearchStatus.INVALID_GOAL;
                effectiveGoal = localGoal;
                return;
            }
            effectiveGoal = normalizedGoal.get().target;
            SearchNode first = start.get();
            SearchNode seeded = new SearchNode(first.key, first.target, MovementType.START, null, 0,
                    heuristic(first.target, effectiveGoal));
            open.add(seeded);
            openByKey.put(seeded.key, seeded);
        }

        public SearchStatus advance(int budget) {
            if (status != SearchStatus.SEARCHING) return status;
            for (int i = 0; i < budget && !open.isEmpty() && expansions < MAX_EXPANSIONS; i++, expansions++) {
                SearchNode current = open.poll();
                if (!openByKey.remove(current.key, current) || !closed.add(current.key)) continue;
                if (horizontalDistance(current.target, effectiveGoal) <= 0.51
                        && Math.abs(current.target.y - effectiveGoal.y) <= 0.76) {
                    result = smooth(reconstruct(current), world);
                    status = SearchStatus.FOUND;
                    return status;
                }
                for (SearchNode unpenalized : successors(world, current, effectiveGoal)) {
                    SearchNode next = penalizedTargets.contains(BlockPos.containing(unpenalized.target))
                            ? new SearchNode(unpenalized.key, unpenalized.target, unpenalized.movement,
                            unpenalized.parent, unpenalized.g + 8.0, unpenalized.h)
                            : unpenalized;
                    if (closed.contains(next.key)) continue;
                    SearchNode existing = openByKey.get(next.key);
                    if (existing == null || next.g < existing.g) {
                        if (existing != null) open.remove(existing);
                        open.add(next);
                        openByKey.put(next.key, next);
                    }
                }
            }
            if (open.isEmpty() || expansions >= MAX_EXPANSIONS) status = SearchStatus.NO_PATH;
            return status;
        }

        public List<PathNode> result() { return result; }
        public Vec3 requestedGoal() { return requestedGoal; }
        public Vec3 effectiveGoal() { return effectiveGoal; }
        public boolean isPartial() { return horizontalDistance(effectiveGoal, requestedGoal) > 2.0; }
    }

    public static Search beginSearch(ServerLevel world, Vec3 start, BlockPos goal, int horizon) {
        return new Search(world, start, goal, horizon, Set.of());
    }

    public static Search beginSearch(ServerLevel world, Vec3 start, BlockPos goal, int horizon,
                                     Set<BlockPos> penalizedTargets) {
        return new Search(world, start, goal, horizon, penalizedTargets);
    }

    /** Compatibility entry point; live navigation advances a Search over ticks. */
    public static List<PathNode> calculatePath(BlockPos start, BlockPos target, ServerLevel world) {
        Search search = beginSearch(world, Vec3.atBottomCenterOf(start), target, NavigationOptions.DEFAULT_HORIZON);
        while (search.advance(2_000) == SearchStatus.SEARCHING) { }
        return search.status == SearchStatus.FOUND ? search.result() : List.of();
    }

    public static List<PathNode> simplifyPath(List<PathNode> path, ServerLevel world) {
        return smooth(path, world);
    }

    public static Queue<Segment> convertPathToSegments(List<PathNode> path, boolean sprint) {
        Queue<Segment> segments = new ArrayDeque<>();
        for (int i = 1; i < path.size(); i++) {
            PathNode from = path.get(i - 1);
            PathNode to = path.get(i);
            segments.add(new Segment(from.target(), to.target(), to.movement(), sprint));
        }
        return segments;
    }

    public static boolean isWaypointStillValid(ServerLevel world, PathNode node) {
        if (node.movement().targetIsWater()) return isWaterCell(world, BlockPos.containing(node.target()));
        return bodyClear(world, node.target()) && hasSupportAt(world, node.target());
    }

    private static List<SearchNode> successors(ServerLevel world, SearchNode current, Vec3 goal) {
        List<SearchNode> out = new ArrayList<>(8);
        if (current.key.water) addVerticalWaterSuccessors(world, current, goal, out);
        for (int[] direction : HORIZONTAL) {
            int x = current.key.x + direction[0];
            int z = current.key.z + direction[1];
            SearchNode ground = bestGroundSuccessor(world, current, x, z, goal);
            if (ground != null) out.add(ground);
            int waterY = (int) Math.floor(current.target.y);
            for (int candidateY : new int[]{waterY, waterY - 1}) {
                BlockPos waterPos = new BlockPos(x, candidateY, z);
                if (isWaterCell(world, waterPos)) {
                    Vec3 target = Vec3.atCenterOf(waterPos);
                    MovementType type = current.key.water ? MovementType.SWIM : MovementType.ENTER_WATER;
                    if (transitionClear(world, current.target, target, type)) {
                        out.add(node(current, keyForWater(waterPos), target, type, goal, movementCost(type)));
                    }
                    break;
                }
            }
        }
        return out;
    }

    private static SearchNode bestGroundSuccessor(ServerLevel world, SearchNode current, int x, int z, Vec3 goal) {
        int baseSupportY = current.key.water ? (int) Math.floor(current.target.y) : current.key.y;
        for (int supportY = baseSupportY + 1; supportY >= baseSupportY - 3; supportY--) {
            Optional<Vec3> landing = groundTarget(world, new BlockPos(x, supportY, z));
            if (landing.isEmpty()) continue;
            Vec3 target = landing.get();
            double rise = target.y - current.target.y;
            MovementType type;
            if (current.key.water) type = MovementType.EXIT_WATER;
            else if (rise > 0.61 && rise <= 1.25) type = MovementType.JUMP_UP;
            else if (rise > EPSILON && rise <= 0.61) type = MovementType.STEP_UP;
            else if (rise >= -0.61) type = MovementType.WALK;
            else if (rise >= -3.01) type = MovementType.DROP;
            else continue;
            // For adjacent walk/step edges, collision-safe endpoints are sufficient: the
            // swept volume is the union of the two neighboring player boxes. Re-running
            // the thin support probe here caused exact block-top positions to be rejected.
            if (type != MovementType.WALK && type != MovementType.STEP_UP
                    && !transitionClear(world, current.target, target, type)) continue;
            double cost = movementCost(type) + Math.max(0, -rise) * 0.35 + clearancePenalty(world, target);
            return node(current, keyForGround(x, supportY, z), target, type, goal, cost);
        }
        return null;
    }

    private static void addVerticalWaterSuccessors(ServerLevel world, SearchNode current, Vec3 goal, List<SearchNode> out) {
        for (int dy : new int[]{1, -1}) {
            BlockPos pos = new BlockPos(current.key.x, current.key.y + dy, current.key.z);
            if (isWaterCell(world, pos)) {
                Vec3 target = Vec3.atCenterOf(pos);
                out.add(node(current, keyForWater(pos), target, MovementType.SWIM, goal, movementCost(MovementType.SWIM) + 0.25));
            }
        }
    }

    private static SearchNode node(SearchNode parent, NavKey key, Vec3 target, MovementType type,
                                   Vec3 goal, double edgeCost) {
        return new SearchNode(key, target, type, parent, parent.g + edgeCost, heuristic(target, goal));
    }

    private static Optional<SearchNode> normalizeStart(ServerLevel world, Vec3 position) {
        BlockPos feet = BlockPos.containing(position);
        if (isWaterCell(world, feet)) {
            return Optional.of(new SearchNode(keyForWater(feet), Vec3.atCenterOf(feet), MovementType.START, null, 0, 0));
        }
        for (int y = feet.getY(); y >= feet.getY() - 3; y--) {
            BlockPos support = new BlockPos(feet.getX(), y, feet.getZ());
            Optional<Vec3> target = groundTarget(world, support);
            if (target.isPresent() && Math.abs(target.get().y - position.y) <= 1.3) {
                return Optional.of(new SearchNode(keyForGround(support.getX(), support.getY(), support.getZ()),
                        target.get(), MovementType.START, null, 0, 0));
            }
        }
        return Optional.empty();
    }

    private static Optional<SearchNode> normalizeGoal(ServerLevel world, BlockPos requested) {
        List<BlockPos> candidates = new ArrayList<>();
        for (int radius = 0; radius <= 2; radius++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) == radius) candidates.add(requested.offset(dx, dy, dz));
                    }
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(p -> p.distSqr(requested)));
        for (BlockPos feet : candidates) {
            if (isWaterCell(world, feet)) {
                return Optional.of(new SearchNode(keyForWater(feet), Vec3.atCenterOf(feet), MovementType.SWIM, null, 0, 0));
            }
            for (int supportY : new int[]{feet.getY() - 1, feet.getY()}) {
                BlockPos support = new BlockPos(feet.getX(), supportY, feet.getZ());
                Optional<Vec3> target = groundTarget(world, support);
                if (target.isPresent()) {
                    return Optional.of(new SearchNode(keyForGround(support.getX(), support.getY(), support.getZ()),
                            target.get(), MovementType.WALK, null, 0, 0));
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Vec3> groundTarget(ServerLevel world, BlockPos support) {
        if (!world.isLoaded(support) || isHazard(world, support)) return Optional.empty();
        VoxelShape shape = world.getBlockState(support).getCollisionShape(world, support);
        if (shape.isEmpty()) return Optional.empty();
        double top = shape.max(Direction.Axis.Y);
        if (top < 0.25 || top > 1.0 + EPSILON) return Optional.empty();
        Vec3 target = new Vec3(support.getX() + 0.5, support.getY() + top, support.getZ() + 0.5);
        BlockPos body = BlockPos.containing(target);
        if (isHazard(world, body) || isWater(world, body)) return Optional.empty();
        return bodyClear(world, target) ? Optional.of(target) : Optional.empty();
    }

    private static boolean bodyClear(ServerLevel world, Vec3 feet) {
        BlockPos pos = BlockPos.containing(feet);
        return world.isLoaded(pos) && world.isLoaded(pos.above())
                && world.noBlockCollision(null, playerBox(feet), false);
    }

    private static boolean hasSupportAt(ServerLevel world, Vec3 feet) {
        int blockX = (int) Math.floor(feet.x);
        int blockY = (int) Math.floor(feet.y - EPSILON);
        int blockZ = (int) Math.floor(feet.z);
        BlockPos support = new BlockPos(blockX, blockY, blockZ);
        if (!world.isLoaded(support)) return false;
        VoxelShape shape = world.getBlockState(support).getCollisionShape(world, support);
        if (shape.isEmpty()) return false;
        return collisionBoxesSupport(shape.toAabbs(), feet.x - blockX, feet.z - blockZ,
                feet.y - blockY);
    }

    static boolean collisionBoxesSupport(List<AABB> boxes, double localX, double localZ, double expectedTop) {
        for (AABB box : boxes) {
            boolean insideX = localX >= box.minX - EPSILON && localX <= box.maxX + EPSILON;
            boolean insideZ = localZ >= box.minZ - EPSILON && localZ <= box.maxZ + EPSILON;
            if (insideX && insideZ && Math.abs(box.maxY - expectedTop) <= 0.08) return true;
        }
        return false;
    }

    private static boolean transitionClear(ServerLevel world, Vec3 from, Vec3 to, MovementType type) {
        int samples = Math.max(2, (int) Math.ceil(from.distanceTo(to) * 4));
        for (int i = 1; i <= samples; i++) {
            double t = i / (double) samples;
            Vec3 point = from.lerp(to, t);
            if (type == MovementType.JUMP_UP) point = new Vec3(point.x, jumpArcY(from.y, to.y, t), point.z);
            if (type == MovementType.STEP_UP || type == MovementType.EXIT_WATER) {
                point = new Vec3(point.x, to.y, point.z);
            }
            if (type == MovementType.DROP) {
                point = t < 0.5
                        ? new Vec3(from.x + (to.x - from.x) * t * 2, from.y, from.z + (to.z - from.z) * t * 2)
                        : new Vec3(to.x, from.y + (to.y - from.y) * (t - 0.5) * 2, to.z);
            }
            if (!bodyClear(world, point)) return false;
            if (type == MovementType.WALK && !hasSupportAt(world, point)) return false;
            if (isHazard(world, BlockPos.containing(point))) return false;
        }
        return true;
    }

    /**
     * Conservative approximation of a vanilla jump: rapid initial lift, a short
     * apex no more than 1.25 blocks above takeoff, then descent onto the landing.
     * A symmetric sine arc rises too late and falsely collides with one-block steps.
     */
    static double jumpArcY(double fromY, double toY, double progress) {
        double t = Math.max(0.0, Math.min(1.0, progress));
        double apex = Math.max(toY, fromY + 1.25);
        if (t <= 0.40) {
            return fromY + (apex - fromY) * (t / 0.40);
        }
        if (t <= 0.65) return apex;
        return apex + (toY - apex) * ((t - 0.65) / 0.35);
    }

    private static boolean isWaterCell(ServerLevel world, BlockPos pos) {
        return world.isLoaded(pos) && isWater(world, pos) && !isHazard(world, pos)
                && world.noBlockCollision(null, playerBox(Vec3.atCenterOf(pos)), false);
    }

    private static boolean isWater(ServerLevel world, BlockPos pos) {
        return world.getFluidState(pos).is(FluidTags.WATER);
    }

    private static boolean isHazard(ServerLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.CACTUS) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW)
                || world.getFluidState(pos).is(FluidTags.LAVA);
    }

    private static double clearancePenalty(ServerLevel world, Vec3 target) {
        int blocked = 0;
        BlockPos feet = BlockPos.containing(target);
        for (int[] direction : HORIZONTAL) {
            BlockPos side = feet.offset(direction[0], 0, direction[1]);
            if (!world.isLoaded(side) || !world.getBlockState(side).getCollisionShape(world, side).isEmpty()) blocked++;
        }
        return blocked * 0.12;
    }

    private static double movementCost(MovementType type) {
        return switch (type) {
            case WALK -> 1.0;
            case STEP_UP -> 1.15;
            case JUMP_UP -> 1.85;
            case DROP -> 1.35;
            case ENTER_WATER, EXIT_WATER -> 2.0;
            case SWIM -> 2.35;
            case START -> 0.0;
        };
    }

    private static List<PathNode> reconstruct(SearchNode end) {
        LinkedList<PathNode> path = new LinkedList<>();
        for (SearchNode node = end; node != null; node = node.parent) {
            path.addFirst(new PathNode(node.target, node.movement, node.key.water ? "water" : "ground", true));
        }
        return path;
    }

    private static List<PathNode> smooth(List<PathNode> path, ServerLevel world) {
        if (path.size() < 3) return List.copyOf(path);
        List<PathNode> smoothed = new ArrayList<>();
        int anchor = 0;
        smoothed.add(path.get(0));
        while (anchor < path.size() - 1) {
            int farthest = anchor + 1;
            for (int candidate = anchor + 2; candidate < path.size(); candidate++) {
                PathNode destination = path.get(candidate);
                boolean boundary = false;
                for (int i = anchor + 1; i <= candidate; i++) {
                    if (path.get(i).movement().isActionBoundary()) { boundary = true; break; }
                }
                if (boundary || Math.abs(path.get(anchor).target().y - destination.target().y) > 0.05
                        || !transitionClear(world, path.get(anchor).target(), destination.target(), MovementType.WALK)) break;
                farthest = candidate;
            }
            PathNode selected = path.get(farthest);
            MovementType movement = farthest > anchor + 1 ? MovementType.WALK : selected.movement();
            smoothed.add(new PathNode(selected.target(), movement, selected.blockName, true));
            anchor = farthest;
        }
        return List.copyOf(smoothed);
    }

    private static AABB playerBox(Vec3 feet) {
        return new AABB(feet.x - HALF_WIDTH, feet.y + EPSILON, feet.z - HALF_WIDTH,
                feet.x + HALF_WIDTH, feet.y + HEIGHT, feet.z + HALF_WIDTH);
    }

    private static NavKey keyForGround(int x, int supportY, int z) { return new NavKey(x, supportY, z, false); }
    private static NavKey keyForWater(BlockPos pos) { return new NavKey(pos.getX(), pos.getY(), pos.getZ(), true); }
    private static double heuristic(Vec3 a, Vec3 b) { return horizontalDistance(a, b) + Math.abs(a.y - b.y) * 0.75; }
    private static double horizontalDistance(Vec3 a, Vec3 b) { return Math.hypot(a.x - b.x, a.z - b.z); }

    private static Vec3 capToLoadedHorizon(ServerLevel world, Vec3 start, Vec3 goal, int horizon) {
        double distance = horizontalDistance(start, goal);
        if (distance <= horizon && world.isLoaded(BlockPos.containing(goal))) return goal;
        double scale = Math.min(1.0, horizon / Math.max(distance, 1.0E-6));
        Vec3 candidate = new Vec3(start.x + (goal.x - start.x) * scale,
                start.y + Math.max(-2, Math.min(2, goal.y - start.y)),
                start.z + (goal.z - start.z) * scale);
        Vec3 directionBack = start.subtract(candidate).normalize();
        while (horizontalDistance(candidate, start) > 8
                && !world.isLoaded(BlockPos.containing(candidate))) {
            candidate = candidate.add(directionBack);
        }
        return candidate;
    }
}
