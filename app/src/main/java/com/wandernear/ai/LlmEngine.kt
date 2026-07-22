package com.wandernear.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A thin wrapper around Google's LiteRT-LM runtime (Gemma 4 E2B).
 *
 * The engine is loaded once and kept alive for the whole app session, because
 * initialize() is slow (~10s+, and much longer for the first load of a 2.6 GB
 * model). A Mutex serialises access so we never load the big model twice or run
 * two generations at once. The model only ever REWORDS text we give it — the
 * grounding guardrails (feeding it only retrieved rows, validating its output)
 * live in the caller, per our never-hallucinate rule.
 */
object LlmEngine {

    private val mutex = Mutex()
    private var engine: Engine? = null

    /** True once the model has been loaded this session (used to pick the UI hint). */
    fun isLoaded(): Boolean = engine != null

    /** Loads + initializes the model once (slow). Returns true if it's ready. */
    suspend fun ensureReady(context: Context): Boolean = mutex.withLock {
        if (engine != null) return@withLock true
        withContext(Dispatchers.IO) {
            val file = ModelManager.modelFile(context)
            if (!file.exists()) return@withContext false
            try {
                val newEngine = Engine(
                    EngineConfig(
                        modelPath = file.absolutePath,
                        backend = Backend.CPU(),           // reliable on the Pixel 6; GPU is a later opt-in
                        cacheDir = context.cacheDir.path,   // speeds up the second load
                        maxNumTokens = 4096,
                    ),
                )
                newEngine.initialize()
                engine = newEngine
                true
            } catch (t: Throwable) {
                engine = null
                false
            }
        }
    }

    /**
     * Rewords [prompt] under the strict [system] instruction, deterministically
     * (temperature 0). Returns the text, or null if the model isn't ready or fails.
     */
    suspend fun generate(system: String, prompt: String): String? = mutex.withLock {
        val activeEngine = engine ?: return@withLock null
        withContext(Dispatchers.IO) {
            try {
                activeEngine.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(system),
                        samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0),
                    ),
                ).use { conversation ->
                    conversation.sendMessage(prompt).toString().trim()
                }
            } catch (t: Throwable) {
                null
            }
        }
    }

    fun close() {
        engine?.close()
        engine = null
    }
}
