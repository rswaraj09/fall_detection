package altermarkive.guardian

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility class for managing audio file recording and organization
 */
class AudioManager(private val context: Context) {
    private val TAG = "AudioManager"
    private var recorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null
    
    companion object {
        // Directory structure for audio files
        private const val BASE_DIR = "voice_assistant"
        private const val LANGUAGE_SUBDIR = "languages"
        private const val RECORDINGS_SUBDIR = "recordings"
        
        /**
         * Get the appropriate directory for storing audio files
         */
        fun getAudioDirectory(context: Context, isRecording: Boolean = false): File {
            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val audioDir = if (isRecording) {
                // Recordings go to a dedicated directory
                File(baseDir, "$BASE_DIR/$RECORDINGS_SUBDIR")
            } else {
                // Language files go to a structured directory
                File(baseDir, "$BASE_DIR/$LANGUAGE_SUBDIR")
            }
            
            if (!audioDir.exists()) {
                audioDir.mkdirs()
            }
            
            return audioDir
        }
        
        /**
         * Get a file path for an audio phrase in a specific language
         */
        fun getLanguageAudioFile(context: Context, phraseKey: String, language: String): File {
            val langDir = File(getAudioDirectory(context), language)
            if (!langDir.exists()) {
                langDir.mkdirs()
            }
            
            return File(langDir, "${phraseKey.lowercase()}.mp3")
        }
        
        /**
         * Copy a raw resource to the file system for use 
         */
        fun copyRawResourceToFile(context: Context, rawResId: Int, outputFile: File): Boolean {
            try {
                context.resources.openRawResource(rawResId).use { input ->
                    FileOutputStream(outputFile).use { output ->
                        val buffer = ByteArray(4096)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                    }
                }
                return true
            } catch (e: IOException) {
                Log.e("AudioManager", "Failed to copy raw resource: ${e.message}")
                return false
            }
        }
    }
    
    /**
     * Start recording audio
     */
    fun startRecording(language: String, phraseKey: String): Boolean {
        // Create file for recording
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = getAudioDirectory(context, true)
        val file = File(dir, "${language}_${phraseKey}_$timestamp.mp3")
        
        try {
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            
            recorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100) // CD quality
                setAudioEncodingBitRate(128000) // Good quality
                setOutputFile(file.absolutePath)
                prepare()
                start()
                currentRecordingFile = file
            }
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording: ${e.message}")
            stopRecording(false)
            return false
        }
    }
    
    /**
     * Stop recording audio
     */
    fun stopRecording(save: Boolean = true): File? {
        try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            
            // If saving, move to the language directory as official recording
            if (save && currentRecordingFile != null && currentRecordingFile!!.exists()) {
                // Parse file name to get language and phrase
                val fileName = currentRecordingFile!!.name
                val parts = fileName.split("_")
                if (parts.size >= 2) {
                    val language = parts[0]
                    val phraseKey = parts[1]
                    
                    // Create destination file
                    val destFile = getLanguageAudioFile(context, phraseKey, language)
                    
                    // Copy file
                    currentRecordingFile!!.copyTo(destFile, true)
                    
                    // Delete original recording
                    currentRecordingFile!!.delete()
                    
                    return destFile
                }
            }
            
            return currentRecordingFile
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}")
            return null
        } finally {
            currentRecordingFile = null
        }
    }
    
    /**
     * Install bundled audio files from raw resources
     */
    fun installBundledAudioFiles() {
        try {
            // Create empty files for each language and phrase if needed
            val languages = listOf("hinglish", "english", "marathi")
            val phrases = listOf("FALL_DETECTED", "CONFIRMATION_RECEIVED")
            
            for (language in languages) {
                for (phrase in phrases) {
                    val outputFile = getLanguageAudioFile(context, phrase, language)
                    if (!outputFile.exists()) {
                        // Create empty file as placeholder
                        // In a real app, we would copy from raw resources
                        outputFile.parentFile?.mkdirs()
                        outputFile.createNewFile()
                        
                        Log.d(TAG, "Created empty placeholder for $language $phrase at ${outputFile.absolutePath}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error installing bundled audio files: ${e.message}")
        }
    }
} 