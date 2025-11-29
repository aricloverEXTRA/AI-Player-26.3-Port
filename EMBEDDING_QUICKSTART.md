# Quick Start: Embedding Provider Setup

## TL;DR - For Users

**You don't need to do anything special!** The embedding system automatically works with whatever LLM provider you choose.

### Basic Setup (Any Provider)

1. **Set your LLM provider** in JVM arguments:
   ```
   -Daiplayer.llmMode=openai
   ```
   (Replace `openai` with your provider: `gemini`, `grok`, `custom`, or `ollama`)

2. **Enter your API key** in the in-game config GUI
   - Press ESC → Mods → AI Player → Configure → API Keys

3. **Done!** Embeddings automatically use your provider

### Provider-Specific Instructions

#### Using OpenAI
```bash
JVM: -Daiplayer.llmMode=openai
GUI: Enter OpenAI API key
Result: Uses text-embedding-3-small automatically
```

#### Using Google Gemini
```bash
JVM: -Daiplayer.llmMode=gemini
GUI: Enter Gemini API key
Result: Uses text-embedding-004 automatically
```

#### Using Grok (xAI)
```bash
JVM: -Daiplayer.llmMode=grok
GUI: Enter Grok API key
Result: Uses text-embedding-ada-002 automatically
```

#### Using Custom Provider (LM Studio, vLLM, etc.)
```bash
JVM: -Daiplayer.llmMode=custom
GUI: 
  - Custom API URL: http://localhost:1234
  - Custom API Key: (if required)
Result: Uses your custom endpoint automatically
```

#### Using Ollama (Default/Fallback)
```bash
JVM: -Daiplayer.llmMode=ollama
(OR omit - defaults to ollama)
Requirement: Ollama running with nomic-embed-text
Result: Uses local Ollama - completely free!
```

### Troubleshooting in 30 Seconds

**Problem:** Embeddings not working  
**Solution:** Check these in order:
1. ✅ API key entered correctly in GUI?
2. ✅ Provider service running/accessible?
3. ✅ Check logs for detailed error
4. ✅ Try Ollama as fallback (always works locally)

**Problem:** "Falling back to Ollama" message  
**This is normal when:**
- Using Claude/Anthropic (no embedding support)
- API key not entered yet
- Provider temporarily unavailable

**Fix:** Either enter your API key, or just use Ollama (make sure it's running)

### Cost Comparison

| Provider | Model | Cost (per 1M tokens) |
|----------|-------|---------------------|
| OpenAI | text-embedding-3-small | $0.02 |
| Gemini | text-embedding-004 | Free tier available |
| Ollama | nomic-embed-text | **$0.00 (local)** |
| Custom | Varies | Varies / $0.00 (local) |

💡 **Pro Tip:** Use Ollama for free, local embeddings even when using cloud LLMs!

### How Automatic Fallback Works

The system intelligently handles embedding failures:

```
Try selected provider (OpenAI/Gemini/etc.)
    ↓
If fails (no API key, network error, etc.)
    ↓
Automatically fall back to Ollama
    ↓
Continue working seamlessly
```

**When does fallback happen?**
- ❌ API key not configured
- ❌ Network/connectivity issues
- ❌ Provider doesn't support embeddings (e.g., Claude)
- ❌ Rate limits hit

**What happens during fallback?**
- ✅ Warning logged: "⚠ [Provider] embeddings unavailable, using Ollama"
- ✅ Ollama takes over automatically
- ✅ Game continues without interruption
- ✅ All features work normally (just locally!)

### Advanced: Using Different Providers for LLM and Embeddings

Want to use **OpenAI for chat** but **Ollama for embeddings** to save money?

**Yes, you can!** Simply:
1. Set `-Daiplayer.llmMode=openai` for your main LLM
2. Keep Ollama running locally with `nomic-embed-text`
3. If OpenAI embeddings fail or you want to save costs, the system automatically falls back to Ollama

**Cost Savings Example:**
- OpenAI GPT-4 for chat: ~$0.03/1K tokens
- Ollama nomic-embed-text for embeddings: **$0.00 (free & local!)**

This gives you the best of both worlds: powerful cloud LLM + free local embeddings!

---

## For Developers

### How It Works Internally

```
User selects LLM provider in JVM args
    ↓
System reads: System.getProperty("aiplayer.llmMode")
    ↓
EmbeddingProviderFactory.createEmbeddingProvider() called
    ↓
Switch on llmMode:
    - "openai" → OpenAI embeddings (text-embedding-3-small)
    - "gemini" → Gemini embeddings (text-embedding-004)  
    - "grok" → Grok embeddings (text-embedding-ada-002)
    - "custom" → Custom endpoint embeddings
    - "claude"/"anthropic" → Fall back to Ollama (no embedding support)
    - default → Ollama (nomic-embed-text)
    ↓
Check if API key configured (ManualConfig)
    ↓
If no key or provider doesn't support embeddings:
    → Fall back to Ollama
    ↓
Return configured EmbeddingProvider instance
    ↓
All RAG/memory operations use this provider
```

**Key Implementation Details:**
- Provider selection is automatic based on `llmMode`
- API keys pulled from `ManualConfig` (user's saved config)
- Ollama is always available as fallback (requires local Ollama running)
- Each provider has a default model (see `getDefaultEmbeddingModel()`)
- All providers use consistent interface via `EmbeddingProvider` class

### Adding a New Provider

```java
// 1. Add to EmbeddingProviderFactory.createEmbeddingProvider()
case "newprovider":
    apiKey = AIPlayer.CONFIG.getNewProviderKey();
    if (apiKey == null || apiKey.isEmpty()) {
        LOGGER.warn("⚠ NewProvider API key not configured, falling back to Ollama");
        return new EmbeddingProvider(ollamaAPI, "nomic-embed-text");
    }
    return new EmbeddingProvider(
        "https://api.newprovider.com",
        apiKey,
        "provider-embedding-model",
        EmbeddingProvider.AIProviderType.OPENAI_COMPATIBLE // or create new type
    );

// 2. Add to getDefaultEmbeddingModel()
case "newprovider":
    return "provider-embedding-model";

// 3. Add getter to ManualConfig.java
public String getNewProviderKey() {
    return newProviderKey;
}

// 4. Test with real credentials
// 5. Update documentation
```

### Testing

```java
// Test automatic provider selection
System.setProperty("aiplayer.llmMode", "openai");
EmbeddingProvider provider = EmbeddingProviderFactory.createEmbeddingProvider(ollamaAPI);
// Should create OpenAI provider with text-embedding-3-small

// Test fallback
System.setProperty("aiplayer.llmMode", "claude");
provider = EmbeddingProviderFactory.createEmbeddingProvider(ollamaAPI);
// Should fallback to Ollama
```

---

**See also:** [EMBEDDING_PROVIDERS.md](./EMBEDDING_PROVIDERS.md) for full documentation

