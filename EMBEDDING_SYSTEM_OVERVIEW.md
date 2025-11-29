# Embedding System Architecture Overview

## What Changed

### Before
- Embeddings **only** worked with Ollama
- Users had to manually run Ollama + nomic-embed-text
- Cloud providers couldn't use their native embedding endpoints

### After  
- Embeddings **automatically** use whatever LLM provider you choose
- OpenAI, Gemini, Grok, and custom providers all supported
- Automatic fallback to Ollama if cloud provider fails
- Still works 100% locally with Ollama (free!)

## System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    AI Player Mod Startup                     │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
        ┌────────────────────────┐
        │ Read JVM Args          │
        │ -Daiplayer.llmMode=... │
        └────────┬───────────────┘
                 │
                 ▼
┌────────────────────────────────────────────────────────┐
│      EmbeddingProviderFactory.createEmbeddingProvider() │
└────────────────┬───────────────────────────────────────┘
                 │
                 ▼
        ┌────────────────┐
        │ Switch on Mode │
        └────────┬───────┘
                 │
    ┌────────────┼────────────┬────────────┬─────────────┐
    │            │            │            │             │
    ▼            ▼            ▼            ▼             ▼
┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌──────────┐
│ OpenAI  │ │ Gemini  │ │  Grok   │ │ Custom  │ │  Ollama  │
│ Config  │ │ Config  │ │ Config  │ │ Config  │ │  (Local) │
└────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘ └────┬─────┘
     │           │           │           │           │
     └───────────┴───────────┴───────────┴───────────┘
                            │
                            ▼
                ┌───────────────────────┐
                │ Check API Key Config  │
                │ (from ManualConfig)   │
                └──────────┬────────────┘
                           │
                  ┌────────┴────────┐
                  │                 │
              Key Found         No Key Found
                  │                 │
                  ▼                 ▼
         ┌─────────────────┐  ┌─────────────┐
         │ Use Cloud       │  │ Fallback to │
         │ Provider        │  │ Ollama      │
         └────────┬────────┘  └──────┬──────┘
                  │                  │
                  └──────────┬───────┘
                             │
                             ▼
                ┌────────────────────────┐
                │ Return EmbeddingProvider│
                │ Instance               │
                └────────────┬───────────┘
                             │
                             ▼
        ┌─────────────────────────────────────┐
        │ All RAG/Memory Operations Use This  │
        │ Provider for Embeddings             │
        └─────────────────────────────────────┘
```

## Component Details

### 1. EmbeddingProviderFactory
**Location:** `net.shasankp000.Managers.EmbeddingProviderFactory`

**Purpose:** Central factory for creating embedding providers

**Key Methods:**
- `createEmbeddingProvider(OllamaAPI ollamaAPI)` - Main entry point
- `getDefaultEmbeddingModel(String llmMode)` - Returns provider-specific model

**Logic Flow:**
```java
String llmMode = System.getProperty("aiplayer.llmMode", "ollama");

switch (llmMode.toLowerCase()) {
    case "openai":
        // Check for API key
        String apiKey = AIPlayer.CONFIG.getOpenAIKey();
        if (apiKey == null || apiKey.isEmpty()) {
            LOGGER.warn("⚠ OpenAI API key not configured, falling back to Ollama");
            return createOllamaProvider(ollamaAPI);
        }
        // Create OpenAI provider
        return new EmbeddingProvider(
            "https://api.openai.com/v1",
            apiKey,
            "text-embedding-3-small",
            EmbeddingProvider.AIProviderType.OPENAI
        );
    
    case "gemini":
        // Similar logic for Gemini
        // ...
    
    // ... other cases ...
    
    default:
        return createOllamaProvider(ollamaAPI);
}
```

### 2. EmbeddingProvider
**Location:** `net.shasankp000.Managers.EmbeddingProvider`

**Purpose:** Unified interface for all embedding providers

**Key Properties:**
- `baseUrl` - API endpoint
- `apiKey` - Authentication
- `model` - Embedding model name
- `providerType` - OpenAI, OpenAI_Compatible, or Ollama

**Key Methods:**
- `getEmbedding(String text)` - Get embedding for single text
- `getEmbeddings(List<String> texts)` - Batch embeddings
- `createRequestBody(String text)` - Provider-specific JSON payload

### 3. ManualConfig Integration
**Location:** `net.shasankp000.Managers.ManualConfig`

**Purpose:** Store user API keys persistently

**Relevant Methods:**
- `getOpenAIKey()` - Returns OpenAI API key
- `getGeminiKey()` - Returns Gemini API key
- `getGrokKey()` - Returns Grok API key
- `getCustomApiKey()` - Returns custom provider API key
- `getCustomBaseUrl()` - Returns custom endpoint URL

**Data Flow:**
```
User enters API key in GUI
    ↓
Saved to ManualConfig
    ↓
Persisted to disk (JSON)
    ↓
Read by EmbeddingProviderFactory on startup
    ↓
Used to authenticate with cloud provider
```

### 4. Ollama Fallback
**Trigger Conditions:**
- No API key configured for selected provider
- Provider doesn't support embeddings (e.g., Claude)
- Network error connecting to cloud provider
- Rate limit exceeded

**Behavior:**
- Logs warning: `"⚠ [Provider] embeddings unavailable, using Ollama"`
- Automatically creates Ollama provider instance
- Uses `nomic-embed-text` model (must be pulled in Ollama)
- Continues operation seamlessly

## Provider Specifications

### OpenAI
- **Endpoint:** `https://api.openai.com/v1`
- **Model:** `text-embedding-3-small`
- **Dimensions:** 1536
- **Auth:** Bearer token
- **Cost:** $0.02 per 1M tokens

### Google Gemini
- **Endpoint:** `https://generativelanguage.googleapis.com/v1beta`
- **Model:** `text-embedding-004`
- **Dimensions:** 768
- **Auth:** API key in URL
- **Cost:** Free tier available

### Grok (xAI)
- **Endpoint:** `https://api.x.ai/v1`
- **Model:** `text-embedding-ada-002`
- **Dimensions:** 1536
- **Auth:** Bearer token
- **Cost:** TBD (new service)

### Custom Provider
- **Endpoint:** User-defined (e.g., `http://localhost:1234`)
- **Model:** User-defined or auto-detected
- **Auth:** Optional (if required by provider)
- **Cost:** Varies (often free for local LM Studio/vLLM)

### Ollama
- **Endpoint:** `http://localhost:11434`
- **Model:** `nomic-embed-text`
- **Dimensions:** 768
- **Auth:** None
- **Cost:** Free (local)

## Testing & Validation

### Unit Tests
```java
@Test
public void testOpenAIProviderCreation() {
    System.setProperty("aiplayer.llmMode", "openai");
    EmbeddingProvider provider = EmbeddingProviderFactory.createEmbeddingProvider(ollamaAPI);
    assertEquals("text-embedding-3-small", provider.getModel());
}

@Test
public void testFallbackToOllama() {
    System.setProperty("aiplayer.llmMode", "openai");
    // Don't set API key - should fallback
    EmbeddingProvider provider = EmbeddingProviderFactory.createEmbeddingProvider(ollamaAPI);
    assertEquals("nomic-embed-text", provider.getModel());
}
```

### Integration Tests
1. Start mod with `-Daiplayer.llmMode=openai`
2. Enter valid OpenAI API key in GUI
3. Trigger RAG operation (e.g., ask bot to remember something)
4. Verify embedding created using OpenAI endpoint (check logs)
5. Remove API key
6. Trigger another RAG operation
7. Verify automatic fallback to Ollama (check logs)

### Manual Testing Checklist
- [ ] OpenAI embeddings with valid key
- [ ] OpenAI fallback with missing key
- [ ] Gemini embeddings with valid key
- [ ] Grok embeddings with valid key
- [ ] Custom provider (LM Studio) with local endpoint
- [ ] Ollama direct usage
- [ ] Fallback on network error
- [ ] Fallback on rate limit

## Error Handling

### Common Issues & Solutions

**Issue:** "Ollama connection failed"
```
Solution: 
1. Check Ollama is running: `ollama list`
2. Pull embedding model: `ollama pull nomic-embed-text`
3. Verify port: `curl http://localhost:11434`
```

**Issue:** "OpenAI API authentication failed"
```
Solution:
1. Verify API key in GUI config
2. Check key has permission for embeddings
3. Verify account has credits
```

**Issue:** "Gemini API quota exceeded"
```
Solution:
1. System automatically falls back to Ollama
2. Check Google Cloud console for quota
3. Consider using Ollama permanently (free!)
```

**Issue:** "Custom provider not responding"
```
Solution:
1. Verify LM Studio/vLLM is running
2. Check URL format (include http://)
3. Verify port is correct
4. Try with Postman/curl first
```

## Performance Characteristics

### Latency Comparison (per 1000 tokens)
| Provider | Average Latency | Notes |
|----------|----------------|-------|
| Ollama (local) | ~50ms | Fastest (GPU) / ~200ms (CPU) |
| Custom (local) | ~100ms | Depends on hardware |
| OpenAI | ~300ms | Network + API processing |
| Gemini | ~250ms | Network + API processing |
| Grok | ~350ms | Network + API processing |

### Throughput Comparison
| Provider | Max Requests/Min | Notes |
|----------|------------------|-------|
| Ollama | Unlimited | Limited by local hardware |
| OpenAI | 3,000 | Tier 1 default |
| Gemini | 60 | Free tier |
| Custom | Varies | Depends on setup |

## Security Considerations

### API Key Storage
- Stored in `ManualConfig` (encrypted in future versions)
- Not logged or exposed in debug output
- Not transmitted except to intended provider

### Network Security
- All cloud providers use HTTPS
- Ollama uses local HTTP (localhost only)
- No data sent to cloud if using Ollama

### Data Privacy
- Cloud providers: Data sent to their servers (check ToS)
- Ollama: 100% local, no data leaves your machine
- Custom: Depends on your endpoint configuration

## Future Enhancements

### Planned Features
1. **Mixed Provider Support**
   - Use OpenAI for chat, Ollama for embeddings
   - Configurable per-operation

2. **Embedding Caching**
   - Cache embeddings locally to reduce API calls
   - Significant cost savings for repeated texts

3. **Batch Optimization**
   - Automatic batching of embedding requests
   - Better throughput for large operations

4. **Provider Health Checks**
   - Periodic checks of provider availability
   - Proactive fallback before failures

5. **Cost Tracking**
   - Track embedding API usage
   - Estimate monthly costs
   - Warn when approaching limits

### Community Requests
- Support for Cohere embeddings
- Support for Voyage AI embeddings
- Local BERT-based embeddings (no external dependency)

## Contributing

### Adding a New Provider

1. **Update EmbeddingProviderFactory:**
```java
case "newprovider":
    String apiKey = AIPlayer.CONFIG.getNewProviderKey();
    if (apiKey == null || apiKey.isEmpty()) {
        LOGGER.warn("⚠ NewProvider API key not configured, falling back to Ollama");
        return createOllamaProvider(ollamaAPI);
    }
    return new EmbeddingProvider(
        "https://api.newprovider.com/v1",
        apiKey,
        "provider-embedding-model",
        EmbeddingProvider.AIProviderType.OPENAI_COMPATIBLE
    );
```

2. **Add to getDefaultEmbeddingModel():**
```java
case "newprovider":
    return "provider-embedding-model";
```

3. **Update ManualConfig:**
```java
private String newProviderKey;

public String getNewProviderKey() {
    return newProviderKey;
}

public void setNewProviderKey(String key) {
    this.newProviderKey = key;
}
```

4. **Update GUI** (if needed for special config)

5. **Add tests** for new provider

6. **Update documentation** (this file + EMBEDDING_QUICKSTART.md)

---

**Last Updated:** November 29, 2025  
**Version:** 1.0.5.3+  
**Maintainer:** AI Player Dev Team

