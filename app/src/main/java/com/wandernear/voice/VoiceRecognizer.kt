package com.wandernear.voice

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Offline speech-to-text with Vosk. The ~40 MB English model ships zipped in the
 * app's assets; on first use it is unzipped once into private storage and then
 * loaded. Vosk runs entirely on-device and returns empty text on silence/noise
 * rather than inventing words — matching the app's never-hallucinate rule.
 */
object VoiceRecognizer {

    private const val MODEL_RESOURCE = "vosk-model-en-us-small.zip"
    private const val SAMPLE_RATE = 16000.0f

    private var model: Model? = null
    private var speechService: SpeechService? = null

    /** Unzips (once) and loads the model. Slow the first time — call off the UI thread. */
    suspend fun ensureModel(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (model != null) return@withContext true
        try {
            val root = File(context.filesDir, "vosk-model")
            if (findModelDir(root) == null) {
                root.deleteRecursively()
                unzipResource(MODEL_RESOURCE, root)
            }
            val modelDir = findModelDir(root) ?: return@withContext false
            model = Model(modelDir.absolutePath)
            true
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * Starts listening. [onPartial] streams the live guess; [onFinal] gives the
     * finished text (also when [stop] is called); [onFail] reports a problem.
     * Vosk delivers these callbacks on the main thread.
     * Returns true if listening actually began (false if it couldn't start), so
     * the caller only shows "Listening…" once the mic is genuinely capturing.
     */
    fun start(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onFail: (String) -> Unit,
    ): Boolean {
        val loaded = model
        if (loaded == null) {
            onFail("Voice model not ready")
            return false
        }
        return try {
            val recognizer = Recognizer(loaded, SAMPLE_RATE)
            val service = SpeechService(recognizer, SAMPLE_RATE)
            service.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    val text = extract(hypothesis, "partial")
                    if (text.isNotBlank()) onPartial(text)
                }

                override fun onResult(hypothesis: String?) {
                    val text = extract(hypothesis, "text")
                    if (text.isNotBlank()) onFinal(text)
                }

                override fun onFinalResult(hypothesis: String?) {
                    val text = extract(hypothesis, "text")
                    if (text.isNotBlank()) onFinal(text)
                }

                override fun onError(exception: Exception?) {
                    onFail(exception?.message ?: "Microphone error")
                }

                override fun onTimeout() { /* no-op */ }
            })
            speechService = service
            true
        } catch (t: Throwable) {
            onFail(t.message ?: "Couldn't start the microphone")
            false
        }
    }

    /** Stops listening (this triggers a final result). */
    fun stop() {
        speechService?.stop()
        speechService = null
    }

    private fun extract(json: String?, key: String): String =
        if (json.isNullOrBlank()) "" else try {
            JSONObject(json).optString(key, "").trim()
        } catch (t: Throwable) {
            ""
        }

    // A valid Vosk model directory contains an "am" (acoustic model) subfolder.
    private fun findModelDir(root: File): File? {
        if (!root.exists()) return null
        if (File(root, "am").exists()) return root
        return root.listFiles()?.firstOrNull { it.isDirectory && File(it, "am").exists() }
    }

    private fun unzipResource(resourceName: String, targetDir: File) {
        targetDir.mkdirs()
        val stream = VoiceRecognizer::class.java.classLoader?.getResourceAsStream(resourceName)
            ?: throw java.io.FileNotFoundException(resourceName)
        stream.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val outFile = File(targetDir, entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { zip.copyTo(it) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
    }
}
