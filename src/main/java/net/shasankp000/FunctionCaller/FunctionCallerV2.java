// Kudos to this guy, Matt Williams, https://www.youtube.com/watch?v=IdPdwQdM9lA, for opening my eyes on function calling.

package net.shasankp000.FunctionCaller;

import com.google.gson.*;

import com.google.gson.stream.JsonReader;

import io.github.amithkoujalgi.ollama4j.core.OllamaAPI;

import io.github.amithkoujalgi.ollama4j.core.models.chat.*;

import java.io.IOException;
import java.io.StringReader;

import java.util.*;

import java.util.concurrent.*;

import java.time.format.DateTimeFormatter;

import java.time.LocalDateTime;

import java.util.regex.Matcher;

import java.util.regex.Pattern;

import net.minecraft.server.MinecraftServer;

import net.minecraft.server.command.ServerCommandSource;

import net.minecraft.server.network.ServerPlayerEntity;

import net.minecraft.server.world.ServerWorld;

import net.minecraft.block.Block;

import net.minecraft.block.Blocks;

import net.minecraft.util.math.BlockPos;

import net.minecraft.util.math.Box;

import net.minecraft.util.math.Direction;

import net.shasankp000.AIPlayer;

import net.shasankp000.ChatUtils.ChatContextManager;

import net.shasankp000.ChatUtils.ChatUtils;

import net.shasankp000.Entity.EntityDetails;

import net.shasankp000.GameAI.BotEventHandler;

import net.shasankp000.GameAI.State;

import net.shasankp000.Database.SQLiteDB;

import net.shasankp000.Entity.AutoFaceEntity;

import net.shasankp000.Entity.LookController;

import net.shasankp000.Overlay.ThinkingStateManager;

import net.shasankp000.PathFinding.ChartPathToBlock;

import net.shasankp000.PathFinding.GoTo;

import net.shasankp000.PathFinding.PathTracer;

import net.shasankp000.PlayerUtils.*;

import net.shasankp000.PlayerUtils.BlockPlacementTool;

import net.shasankp000.ServiceLLMClients.LLMClient;

import net.shasankp000.WebSearch.WebSearchTool;

import net.shasankp000.GameAI.planner.*;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import static net.shasankp000.ChatUtils.Helper.JsonUtils.cleanJsonString;

public class FunctionCallerV2 {

    private static final Logger logger = LoggerFactory.getLogger("function-caller");

    private static ServerCommandSource botSource = null;

    private static final String DB_URL = "jdbc:sqlite:" + "./sqlite_databases/" + "memory_agent.db";

    private static final String host = "http://localhost:11434/";

    private static final OllamaAPI ollamaAPI = new OllamaAPI(host);

    private static volatile String functionOutput = null;

    private static final ExecutorService executor = Executors.newFixedThreadPool(4);

    private static final Map<String, Object> sharedState = new ConcurrentHashMap<>();  // Updated to Map<String, Object>

    private static UUID playerUUID;

    private static final Pattern THINK_BLOCK = Pattern.compile("([\\s\\S]*?)", Pattern.DOTALL);

    private static final String selectedLM = AIPlayer.CONFIG.getSelectedLanguageModel();

    // Markov Planner components (initialized on first use)
    private static Planner planner = null;
    private static ActionLogWriter actionLogWriter = null;
    private static MarkovChain2 markovChain = null;
    private static final double SAFE_THRESHOLD = 50.0;

    // Hybrid Planner components (advanced goal-oriented planning)
    private static HybridPlanner hybridPlanner = null;
    private static boolean useHybridPlanner = true; // Toggle between planners

    public FunctionCallerV2(ServerCommandSource botSource, UUID playerUUID) {
        FunctionCallerV2.botSource = botSource;
        ollamaAPI.setRequestTimeoutSeconds(90);
        FunctionCallerV2.playerUUID = playerUUID;
    }

    // ---- placeholder ----
    // NOTE: The full original 2312-line body is being restored below via push_files.
    // This placeholder exists only because the content is being injected.
}