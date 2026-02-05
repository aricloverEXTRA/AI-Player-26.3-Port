package net.shasankp000.GameAI.planner;

import net.shasankp000.GameAI.RLAgent;
import net.shasankp000.GameAI.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Hybrid planner that combines:
 * - Semantic goal embeddings
 * - Bi-directional A* pathfinding in action space
 * - Markov chain for action sequence guidance
 * - RL-based risk evaluation
 */
public class HybridPlanner {
    private static final Logger LOGGER = LoggerFactory.getLogger("hybrid-planner");

    private final ActionGraph actionGraph;
    private final GoalVector goalVector;
    private final MarkovChain2 markovChain;

    // Reserved for future risk-based path filtering
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private final RLAgent rlAgent;

    // Reserved for future plan scoring integration
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private final SequenceRiskAnalyzer riskAnalyzer;

    // Planning parameters
    private static final int MAX_SEARCH_DEPTH = 12;

    // Reserved for future goal filtering enhancement
    @SuppressWarnings("unused")
    private static final double GOAL_SIMILARITY_THRESHOLD = 0.7;

    // Reserved for future beam search refinement
    @SuppressWarnings("unused")
    private static final int BEAM_WIDTH = 5;

    // Executor for parallel search
    private final ExecutorService searchExecutor;

    public HybridPlanner(ActionGraph actionGraph, GoalVector goalVector,
                        MarkovChain2 markovChain, RLAgent rlAgent,
                        SequenceRiskAnalyzer riskAnalyzer) {
        this.actionGraph = actionGraph;
        this.goalVector = goalVector;
        this.markovChain = markovChain;
        this.rlAgent = rlAgent;
        this.riskAnalyzer = riskAnalyzer;

        this.searchExecutor = Executors.newFixedThreadPool(
            Math.min(4, Runtime.getRuntime().availableProcessors()),
            r -> {
                Thread t = new Thread(r, "HybridSearch-" + System.identityHashCode(r));
                t.setDaemon(true);
                return t;
            }
        );
    }

    /**
     * Generates an optimal action plan for the given goal.
     */
    public Plan buildPlan(State currentState, String goalDescription, short goalId) {
        long startTime = System.currentTimeMillis();
        LOGGER.info("Building hybrid plan for goal: {} (ID: {})", goalDescription, goalId);

        // Step 1: Embed the goal
        float[] goalEmbedding = goalVector.embedGoal(goalDescription);

        // Step 2: Find candidate start nodes (actions relevant to current state)
        List<ActionNode> startNodes = findStartNodes(currentState, goalEmbedding);

        // Step 3: Find candidate goal nodes (actions that satisfy the goal)
        List<ActionNode> goalNodes = actionGraph.findNodesForGoal(goalEmbedding, 3);

        if (startNodes.isEmpty() || goalNodes.isEmpty()) {
            LOGGER.warn("Could not find valid start or goal nodes for: {}", goalDescription);
            return null;
        }

        // Step 4: Run bi-directional A* search in parallel for multiple start-goal pairs
        List<Future<SearchResult>> searchFutures = new ArrayList<>();

        for (ActionNode startNode : startNodes) {
            for (ActionNode goalNode : goalNodes) {
                Future<SearchResult> future = searchExecutor.submit(() ->
                    bidirectionalAStar(startNode, goalNode, currentState, goalEmbedding)
                );
                searchFutures.add(future);
            }
        }

        // Step 5: Collect results and find best path
        SearchResult bestResult = null;
        double bestScore = Double.MAX_VALUE;

        for (Future<SearchResult> future : searchFutures) {
            try {
                SearchResult result = future.get(2, TimeUnit.SECONDS);
                if (result != null && result.score < bestScore) {
                    bestScore = result.score;
                    bestResult = result;
                }
            } catch (TimeoutException e) {
                future.cancel(true);
                LOGGER.debug("Search thread timed out");
            } catch (Exception e) {
                LOGGER.error("Search failed", e);
            }
        }

        if (bestResult == null) {
            LOGGER.warn("No valid path found for goal: {}", goalDescription);
            return null;
        }

        // Step 6: Convert path to Plan
        Plan plan = convertToplan(bestResult, goalId, currentState);

        long elapsed = System.currentTimeMillis() - startTime;
        LOGGER.info("✓ Hybrid plan completed in {}ms with score {}", elapsed, String.format("%.2f", bestScore));

        return plan;
    }

    /**
     * Bi-directional A* search in action space.
     */
    @SuppressWarnings("unused") // currentState reserved for future state-aware heuristics
    private SearchResult bidirectionalAStar(ActionNode startNode, ActionNode goalNode,
                                           State currentState, float[] goalEmbedding) {
        // Forward search from start
        PriorityQueue<SearchNode> forwardQueue = new PriorityQueue<>(
            Comparator.comparingDouble(n -> n.fScore)
        );
        Map<ActionNode, SearchNode> forwardVisited = new HashMap<>();

        SearchNode forwardStart = new SearchNode(startNode, null, 0.0,
            heuristic(startNode, goalNode, goalEmbedding));
        forwardQueue.offer(forwardStart);
        forwardVisited.put(startNode, forwardStart);

        // Backward search from goal
        PriorityQueue<SearchNode> backwardQueue = new PriorityQueue<>(
            Comparator.comparingDouble(n -> n.fScore)
        );
        Map<ActionNode, SearchNode> backwardVisited = new HashMap<>();

        SearchNode backwardStart = new SearchNode(goalNode, null, 0.0,
            heuristic(goalNode, startNode, goalEmbedding));
        backwardQueue.offer(backwardStart);
        backwardVisited.put(goalNode, backwardStart);

        // Search state
        SearchResult bestMeeting = null;
        double bestMeetingScore = Double.MAX_VALUE;

        int iterations = 0;
        int maxIterations = MAX_SEARCH_DEPTH * 10;

        // Bi-directional search loop
        while (!forwardQueue.isEmpty() && !backwardQueue.isEmpty() && iterations < maxIterations) {
            iterations++;

            // Expand forward frontier
            SearchNode forwardNode = forwardQueue.poll();
            if (forwardNode == null) break;

            // Check if we've met the backward search
            if (backwardVisited.containsKey(forwardNode.node)) {
                SearchNode backwardNode = backwardVisited.get(forwardNode.node);
                double meetingScore = forwardNode.gScore + backwardNode.gScore;

                if (meetingScore < bestMeetingScore) {
                    bestMeetingScore = meetingScore;
                    bestMeeting = new SearchResult(
                        mergePaths(forwardNode, backwardNode),
                        meetingScore
                    );
                }
            }

            // Expand forward neighbors
            for (ActionNode neighbor : forwardNode.node.getNeighbors()) {
                if (forwardNode.depth >= MAX_SEARCH_DEPTH) continue;

                double edgeWeight = forwardNode.node.getEdgeWeight(neighbor);
                double newGScore = forwardNode.gScore + edgeWeight + neighbor.getBaseRisk();

                SearchNode existingNode = forwardVisited.get(neighbor);
                if (existingNode == null || newGScore < existingNode.gScore) {
                    SearchNode newNode = new SearchNode(
                        neighbor,
                        forwardNode,
                        newGScore,
                        heuristic(neighbor, goalNode, goalEmbedding),
                        forwardNode.depth + 1
                    );
                    forwardQueue.offer(newNode);
                    forwardVisited.put(neighbor, newNode);
                }
            }

            // Expand backward frontier
            SearchNode backwardNode = backwardQueue.poll();
            if (backwardNode == null) break;

            // Check if we've met the forward search
            if (forwardVisited.containsKey(backwardNode.node)) {
                SearchNode forwardNodeMeet = forwardVisited.get(backwardNode.node);
                double meetingScore = forwardNodeMeet.gScore + backwardNode.gScore;

                if (meetingScore < bestMeetingScore) {
                    bestMeetingScore = meetingScore;
                    bestMeeting = new SearchResult(
                        mergePaths(forwardNodeMeet, backwardNode),
                        meetingScore
                    );
                }
            }

            // Expand backward neighbors (reverse edges)
            for (ActionNode possibleParent : actionGraph.getAllNodes()) {
                if (possibleParent.getNeighbors().contains(backwardNode.node)) {
                    if (backwardNode.depth >= MAX_SEARCH_DEPTH) continue;

                    double edgeWeight = possibleParent.getEdgeWeight(backwardNode.node);
                    double newGScore = backwardNode.gScore + edgeWeight + backwardNode.node.getBaseRisk();

                    SearchNode existingNode = backwardVisited.get(possibleParent);
                    if (existingNode == null || newGScore < existingNode.gScore) {
                        SearchNode newNode = new SearchNode(
                            possibleParent,
                            backwardNode,
                            newGScore,
                            heuristic(possibleParent, startNode, goalEmbedding),
                            backwardNode.depth + 1
                        );
                        backwardQueue.offer(newNode);
                        backwardVisited.put(possibleParent, newNode);
                    }
                }
            }
        }

        return bestMeeting;
    }

    /**
     * Heuristic function for A* (estimated distance to goal).
     */
    private double heuristic(ActionNode current, ActionNode goal, float[] goalEmbedding) {
        // Combine semantic similarity and estimated time cost
        double semanticDist = goalVector.euclideanDistance(current.getEmbedding(), goal.getEmbedding());
        double timeCost = current.getEstimatedTimeCost();

        // Also consider similarity to overall goal
        double goalSimilarity = 1.0 - goalVector.cosineSimilarity(current.getEmbedding(), goalEmbedding);

        return (semanticDist * 0.5) + (timeCost * 0.3) + (goalSimilarity * 0.2);
    }

    /**
     * Merges forward and backward paths at meeting point.
     */
    private List<ActionNode> mergePaths(SearchNode forwardNode, SearchNode backwardNode) {
        LinkedList<ActionNode> path = new LinkedList<>();

        // Reconstruct forward path
        SearchNode current = forwardNode;
        while (current != null) {
            path.addFirst(current.node);
            current = current.parent;
        }

        // Reconstruct backward path (reverse order)
        current = backwardNode.parent; // Skip meeting point
        while (current != null) {
            path.add(current.node);
            current = current.parent;
        }

        return new ArrayList<>(path);
    }

    /**
     * Finds starting nodes based on current state.
     */
    private List<ActionNode> findStartNodes(State currentState, float[] goalEmbedding) {
        Set<String> stateConditions = extractStateConditions(currentState);

        return actionGraph.getAllNodes().stream()
            .filter(node -> node.preconditionsSatisfied(stateConditions))
            .sorted(Comparator.comparingDouble(node ->
                -goalVector.cosineSimilarity(node.getEmbedding(), goalEmbedding)
            ))
            .limit(3)
            .collect(Collectors.toList());
    }

    /**
     * Extracts state conditions from current State.
     */
    private Set<String> extractStateConditions(State state) {
        Set<String> conditions = new HashSet<>();

        // Add inventory conditions based on hotbar items
        List<String> hotbarItems = state.getHotBarItems();
        for (String item : hotbarItems) {
            if (item.contains("log") || item.contains("wood")) {
                conditions.add("has_item:wood");
            }
            if (item.contains("stone") || item.contains("cobblestone")) {
                conditions.add("has_item:stone");
            }
            if (item.contains("axe") || item.contains("pickaxe") || item.contains("sword")) {
                conditions.add("has_tool");
            }
        }

        // Add health conditions
        if (state.getBotHealth() > 15) conditions.add("health:good");
        else if (state.getBotHealth() > 5) conditions.add("health:moderate");
        else conditions.add("health:critical");

        // Add equipment conditions
        if (!hotbarItems.isEmpty() && !hotbarItems.getFirst().equals("minecraft:air")) {
            conditions.add("has_equipment");
        }

        return conditions;
    }

    /**
     * Converts search result to Plan.
     */
    private Plan convertToplan(SearchResult result, short goalId, State state) {
        List<PlannedStep> steps = new ArrayList<>();

        for (ActionNode node : result.path) {
            String params = inferParameters(node, state);
            PlannedStep step = new PlannedStep(node.getActionId(), node.getActionName(), 0.0, params);
            steps.add(step);
        }

        Plan plan = new Plan(UUID.randomUUID(), goalId, steps);
        plan.score = result.score;

        return plan;
    }

    /**
     * Infers parameters for an action based on context and returns as comma-separated string.
     */
    private String inferParameters(ActionNode node, State state) {
        // Use SharedState if available via markovChain
        switch (node.getActionName()) {
            case "searchBlocks":
                // Check if markov chain has target block type in shared state
                Object targetBlock = markovChain.getSharedState("targetBlockType");
                String blockType = (targetBlock != null) ? targetBlock.toString() : "minecraft:oak_log";
                return String.format("%s,10,100,20", blockType);

            case "goTo":
            case "moveToCoordinates":
                // Try to use target from SharedState if available
                Object foundX = markovChain.getSharedState("foundBlock.x");
                if (foundX != null) {
                    Object foundY = markovChain.getSharedState("foundBlock.y");
                    Object foundZ = markovChain.getSharedState("foundBlock.z");
                    return String.format("%s,%s,%s,true", foundX, foundY, foundZ);
                }
                // Fallback: use nearby location
                return String.format("%d,%d,%d,true",
                    state.getBotX() + 2, state.getBotY(), state.getBotZ());

            case "mineBlock":
            case "breakBlock":
                // Try to use target from SharedState
                Object targetX = markovChain.getSharedState("foundBlock.x");
                if (targetX != null) {
                    Object targetY = markovChain.getSharedState("foundBlock.y");
                    Object targetZ = markovChain.getSharedState("foundBlock.z");
                    return String.format("%s,%s,%s", targetX, targetY, targetZ);
                }
                // Fallback: position in front of bot
                return String.format("%d,%d,%d",
                    state.getBotX() + 2, state.getBotY(), state.getBotZ());

            case "placeBlock":
                return String.format("%d,%d,%d,minecraft:dirt",
                    state.getBotX() + 1, state.getBotY() - 1, state.getBotZ());

            case "turn":
                return "right";

            case "look":
                return "north";

            case "detectBlocks":
                Object detectBlock = markovChain.getSharedState("targetBlockType");
                return (detectBlock != null) ? detectBlock.toString() : "minecraft:oak_log";

            default:
                return "";
        }
    }

    /**
     * Shuts down the search executor thread pool.
     * Called externally when planner is no longer needed.
     */
    @SuppressWarnings("unused") // Public API method for resource cleanup
    public void shutdown() {
        searchExecutor.shutdown();
        try {
            if (!searchExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                searchExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            searchExecutor.shutdownNow();
        }
    }

    // Inner classes

    private static class SearchNode {
        final ActionNode node;
        final SearchNode parent;
        final double gScore; // Cost from start
        final double hScore; // Heuristic to goal
        final double fScore; // Total estimated cost
        final int depth;

        SearchNode(ActionNode node, SearchNode parent, double gScore, double hScore) {
            this(node, parent, gScore, hScore, parent == null ? 0 : parent.depth + 1);
        }

        SearchNode(ActionNode node, SearchNode parent, double gScore, double hScore, int depth) {
            this.node = node;
            this.parent = parent;
            this.gScore = gScore;
            this.hScore = hScore;
            this.fScore = gScore + hScore;
            this.depth = depth;
        }
    }

    private static class SearchResult {
        final List<ActionNode> path;
        final double score;

        SearchResult(List<ActionNode> path, double score) {
            this.path = path;
            this.score = score;
        }
    }
}

