package net.shasankp000.GameAI.planner;

import net.minecraft.server.network.ServerPlayerEntity;
import net.shasankp000.GameAI.RLAgent;
import net.shasankp000.GameAI.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * Main planner: generates and scores action plans using Markov chains and RL risk analysis.
 * Implements parallel planning with beam search refinement.
 */
public class Planner {
    private static final Logger LOGGER = LoggerFactory.getLogger(Planner.class);

    // Configuration constants
    private static final int INITIAL_DRAFTS = 4;
    private static final int BEAM_WIDTH = 3;
    private static final int MAX_REFINEMENT_ITERS = 6;
    private static final double SAFE_THRESHOLD = 50.0;
    private static final double EXPLORATION_EPSILON = 0.15;
    private static final int MAX_PLAN_LENGTH = 12;
    private static final int MIN_PLAN_LENGTH = 3;

    private final MarkovChain2 markovChain;
    private final SequenceRiskAnalyzer riskAnalyzer;
    private final ExecutorService executor;
    private final Random random;

    public Planner(MarkovChain2 markovChain, RLAgent rlAgent, ServerPlayerEntity bot) {
        this.markovChain = markovChain;
        this.riskAnalyzer = new SequenceRiskAnalyzer(rlAgent, bot);
        this.executor = Executors.newFixedThreadPool(4);
        this.random = new Random();
    }

    /**
     * Build a plan for the given goal.
     */
    public Plan buildPlan(State currentState, short goalId) {
        LOGGER.info("[planner] Building plan for goal {} with state HP={}", goalId, currentState.getBotHealth());

        // Generate initial drafts in parallel
        List<Plan> drafts = generateDraftsParallel(currentState, goalId);

        if (drafts.isEmpty()) {
            LOGGER.warn("[planner] No valid drafts generated");
            return null;
        }

        // Score and select best drafts
        List<Plan> beam = selectTopPlans(drafts, BEAM_WIDTH);

        // Iterative refinement
        for (int iter = 0; iter < MAX_REFINEMENT_ITERS; iter++) {
            List<Plan> neighbors = generateNeighborsParallel(beam, currentState, goalId);

            if (neighbors.isEmpty()) {
                break;
            }

            beam = selectTopPlans(neighbors, BEAM_WIDTH);

            if (beam.get(0).getTotalScore() < SAFE_THRESHOLD) {
                break;
            }
        }

        Plan bestPlan = beam.get(0);

        if (bestPlan.getTotalScore() < SAFE_THRESHOLD * 4) {
            return bestPlan;
        }

        return null;
    }

    private List<Plan> generateDraftsParallel(State state, short goalId) {
        List<CompletableFuture<Plan>> futures = new ArrayList<>();

        for (int i = 0; i < INITIAL_DRAFTS; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> generateDraft(state, goalId), executor));
        }

        return futures.stream()
                .map(f -> {
                    try {
                        return f.get(1, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private Plan generateDraft(State state, short goalId) {
        try {
            int length = MIN_PLAN_LENGTH + random.nextInt(MAX_PLAN_LENGTH - MIN_PLAN_LENGTH);
            List<PlannedStep> steps = markovChain.draftPlan(goalId, state, length, EXPLORATION_EPSILON);

            if (steps.isEmpty()) {
                return null;
            }

            double score = riskAnalyzer.scoreSequence(steps, state, goalId);
            return new Plan(UUID.randomUUID(), goalId, steps, score);
        } catch (Exception e) {
            LOGGER.error("[planner] Draft generation error: {}", e.getMessage());
            return null;
        }
    }

    private List<Plan> generateNeighborsParallel(List<Plan> beam, State state, short goalId) {
        List<Plan> allNeighbors = Collections.synchronizedList(new ArrayList<>());

        beam.parallelStream().forEach(plan -> {
            allNeighbors.addAll(generateNeighbors(plan, state, goalId));
        });

        return allNeighbors;
    }

    private List<Plan> generateNeighbors(Plan plan, State state, short goalId) {
        List<Plan> neighbors = new ArrayList<>();
        List<PlannedStep> originalSteps = plan.getSteps();

        if (originalSteps.isEmpty() || originalSteps.size() < 2) {
            return neighbors;
        }

        // Neighbor 1: Replace random segment
        try {
            int start = random.nextInt(originalSteps.size() - 1);
            int end = Math.min(originalSteps.size(), start + 2);
            List<PlannedStep> newSteps = new ArrayList<>(originalSteps);
            newSteps.subList(start, end).clear();
            List<PlannedStep> replacement = markovChain.draftPlan(goalId, state, end - start, 0.3);
            newSteps.addAll(start, replacement);
            double score = riskAnalyzer.scoreSequence(newSteps, state, goalId);
            neighbors.add(new Plan(UUID.randomUUID(), goalId, newSteps, score));
        } catch (Exception e) {
            // Skip this neighbor on error
        }

        // Neighbor 2: Insert safety action
        if (originalSteps.size() < MAX_PLAN_LENGTH && state.getBotHealth() < 10) {
            try {
                List<PlannedStep> newSteps = new ArrayList<>(originalSteps);
                byte safetyAction = ActionMapper.getActionId("eat_food");
                newSteps.add(random.nextInt(newSteps.size() + 1),
                           new PlannedStep(safetyAction, "eat_food", 0.0, null));
                double score = riskAnalyzer.scoreSequence(newSteps, state, goalId);
                neighbors.add(new Plan(UUID.randomUUID(), goalId, newSteps, score));
            } catch (Exception e) {
                // Skip this neighbor on error
            }
        }

        return neighbors;
    }

    private List<Plan> selectTopPlans(List<Plan> plans, int topK) {
        return plans.stream()
                .sorted(Comparator.comparingDouble(Plan::getTotalScore))
                .limit(topK)
                .toList();
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

