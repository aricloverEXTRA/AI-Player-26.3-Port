# Automatic Embedding Provider System

## Overview

The AI Player mod now features a **fully automatic embedding provider system** that seamlessly integrates with your chosen LLM provider. You don't need to manually configure embedding models - the system automatically selects the best embedding model based on your LLM provider.

## How It Works

### 🎯 Zero Configuration

1. **Set your LLM provider** via JVM arguments (e.g., `-Daiplayer.llmMode=openai`)
2. **Enter your API key** in the in-game configuration GUI
3. **That's it!** The embedding system automatically:
   - Detects your LLM provider
   - Selects the appropriate embedding endpoint
   - Uses the optimal embedding model for that provider
   - Falls back to Ollama if needed

### 📊 Supported Providers & Models

| Provider | Embedding Model | Endpoint | API Key Required |
|----------|----------------|----------|------------------|
| **Ollama** (default) | `nomic-embed-text` | `http://localhost:11434` | ❌ No |
| **OpenAI** | `text-embedding-3-small` | `https://api.openai.com` | ✅ Yes |
| **Google Gemini** | `text-embedding-004` | `https://generativelanguage.googleapis.com` | ✅ Yes |
| **xAI/Grok** | `text-embedding-ada-002` | `https://api.x.ai` | ✅ Yes |
| **Custom (OpenAI-compatible)** | `text-embedding-ada-002` | Your custom URL | ⚠️ Maybe |
| **Anthropic/Claude** | N/A (fallback to Ollama) | N/A | N/A |

### 🔧 Custom Provider Support

For **custom OpenAI-compatible providers** (LM Studio, vLLM, TabbyAPI, etc.):

1. Set `-Daiplayer.llmMode=custom` in JVM arguments
2. Configure your custom API URL in the settings GUI
3. (Optional) Enter API key if required by your endpoint
4. The system automatically uses your custom endpoint for embeddings

**Examples:**
- **LM Studio:** `http://localhost:1234`
- **vLLM:** `http://localhost:8000`
- **TabbyAPI:** `http://localhost:5000`

### ⚠️ Fallback Behavior

The system automatically falls back to **Ollama (nomic-embed-text)** when:

- API key is missing or invalid
- Provider doesn't support embeddings (e.g., Anthropic/Claude)
- Custom endpoint is not configured
- Network/connection issues occur

**Note:** When using fallback, make sure Ollama is running locally.

## Benefits

### For Users
- ✅ **No manual configuration** - works out of the box
- ✅ **Cost-effective** - uses latest, most efficient embedding models
- ✅ **Seamless integration** - automatically matches your LLM provider
- ✅ **Intelligent fallback** - always has a working configuration

### For Developers
- ✅ **Type-safe provider enumeration**
- ✅ **Centralized configuration** via `EmbeddingProviderFactory`
- ✅ **Easy to extend** with new providers
- ✅ **Comprehensive error handling**

## Technical Details

### Architecture

```
EmbeddingProviderFactory
    ↓
Reads: System.getProperty("aiplayer.llmMode")
    ↓
Reads: AIPlayer.CONFIG (ManualConfig)
    ↓
Creates: EmbeddingProvider with appropriate:
    - Endpoint URL
    - API Key
    - Embedding Model
    - Provider Type
```

### Provider Detection Flow

1. **Read JVM property:** `aiplayer.llmMode`
2. **Match provider type:**
   - `ollama` → Use Ollama API
   - `openai` → Use OpenAI endpoint + API key
   - `gemini` → Use Gemini endpoint + API key
   - `grok` → Use Grok endpoint + API key
   - `custom` → Use custom endpoint + optional API key
   - `claude`/`anthropic` → Fallback to Ollama
3. **Validate configuration:**
   - Check API key availability
   - Verify endpoint accessibility
4. **Create provider** with optimal settings

### Code Location

- **Factory:** `net.shasankp000.AIProviders.EmbeddingProviderFactory`
- **Provider:** `net.shasankp000.AIProviders.EmbeddingProvider`
- **Config:** `net.shasankp000.FilingSystem.ManualConfig`
- **Usage:** All RAG-related classes (e.g., `OldRAGImplementation`)

## Troubleshooting

### "Failed to create embedding provider, falling back to Ollama"

**Cause:** Issue with your configured provider (API key, endpoint, etc.)

**Solution:**
1. Check your API key is correctly entered in the config GUI
2. For custom providers, verify the endpoint URL is correct
3. Ensure your provider service is running/accessible
4. Check the logs for more detailed error messages

### Embeddings not working with custom provider

**Possible causes:**
- Custom endpoint doesn't support OpenAI-compatible embedding endpoints
- API key is required but not provided
- Endpoint URL is incorrect

**Solution:**
- Verify your custom provider supports `/v1/embeddings` endpoint
- Check provider documentation for embedding model names
- Try with Ollama as fallback to verify RAG functionality

### High embedding costs with OpenAI/Gemini

**Note:** The selected models are the **most cost-effective options** for each provider:
- OpenAI: `text-embedding-3-small` (cheapest, latest)
- Gemini: `text-embedding-004` (free tier available)

If costs are still a concern, consider using:
- **Ollama** (completely free, local)
- **Custom local providers** (LM Studio, vLLM with local models)

## Examples

### Example 1: Using OpenAI

```bash
# JVM arguments
-Daiplayer.llmMode=openai

# In-game config
OpenAI API Key: sk-...
```

**Result:** Automatically uses `text-embedding-3-small` from OpenAI

### Example 2: Using Custom Provider (LM Studio)

```bash
# JVM arguments
-Daiplayer.llmMode=custom

# In-game config
Custom API URL: http://localhost:1234
Custom API Key: (leave empty if not required)
```

**Result:** Automatically uses your local LM Studio for embeddings

### Example 3: Using Ollama (Default)

```bash
# JVM arguments
-Daiplayer.llmMode=ollama
# OR omit entirely (defaults to ollama)

# Requirements
- Ollama running locally
- nomic-embed-text model pulled
```

**Result:** Uses local Ollama for free embeddings

## FAQ

**Q: Do I need to install nomic-embed-text if I'm using OpenAI?**  
A: No, the system automatically uses OpenAI's embedding endpoint. You only need nomic-embed-text if using Ollama or as fallback.

**Q: Can I change the embedding model?**  
A: Currently, the system uses optimal defaults. If you need a different model, you can modify `EmbeddingProviderFactory.getDefaultEmbeddingModel()`.

**Q: What if my custom provider uses a different embedding model name?**  
A: The default `text-embedding-ada-002` is widely compatible. If your provider requires a different name, you can modify the factory or use Ollama.

**Q: Does this work with the memory/RAG system?**  
A: Yes! This is specifically designed for the RAG and memory systems. All embedding calls automatically use your configured provider.

**Q: Is there any performance difference between providers?**  
A: Cloud providers (OpenAI, Gemini) may have slightly higher latency but better quality. Local providers (Ollama, LM Studio) are faster but require local resources.

## Contributing

To add support for a new provider:

1. Add case to `EmbeddingProviderFactory.createEmbeddingProvider()`
2. Add default model to `getDefaultEmbeddingModel()`
3. Ensure provider type is handled in `EmbeddingProvider`
4. Test with real API credentials
5. Update this documentation

---

**Last Updated:** November 29, 2025  
**Version:** 1.0.6+

