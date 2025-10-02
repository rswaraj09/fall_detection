package altermarkive.guardian

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

// Import our custom AudioManager as a different name to avoid conflicts
import altermarkive.guardian.AudioManager as VoiceAudioManager

/**
 * A comprehensive voice assistant that handles speech recognition and voice prompts
 * with multi-language support
 */
class VoiceAssistant private constructor(private val context: Context) {
    private val TAG = "VoiceAssistant"
    private val isListening = AtomicBoolean(false)
    private var mediaPlayer: MediaPlayer? = null
    private var textToSpeech: TextToSpeech? = null
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()
    
    // Language preferences and dictionaries
    private var currentLanguage = "english" // Default language
    private var useTTS = false // Whether to use TTS or pre-recorded audio
    
    // Speech recognition components
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Audio management
    private val audioManager = VoiceAudioManager(context)
    
    // Callbacks
    private var fallConfirmationCallback: ((Boolean) -> Unit)? = null
    private var generalResultCallback: ((String?) -> Unit)? = null
    
    // Current conversation state
    private var currentConversationState = ConversationState.IDLE
    
    // A reference to pending speech results to process
    private var pendingResults: ArrayList<String>? = null
    
    // Timeouts
    private val RECOGNITION_TIMEOUT_MS = 10000L // 10 seconds
    private var timeoutRunnable: Runnable? = null
    
    // Phrases in different languages using natural Indian tones
    private val phrases = mapOf(
        "FALL_DETECTED" to mapOf(
            "english" to "We detected a fall. Are you okay?",
            "hinglish" to "Kya aap theek hai?",
            "marathi" to "Tumhi theek aahat ka?"
        ),
        "CONFIRMATION_RECEIVED" to mapOf(
            "english" to "We received your confirmation. Take care.",
            "hinglish" to "Aapke confirmation mil gaya hai. Apna dhyan rakhiye.",
            "marathi" to "Tumcha confirmation milala aahe. Swataachi kaaljee ghya."
        ),
        "NO_RESPONSE" to mapOf(
            "english" to "No response detected. Trying again.",
            "hinglish" to "Koi jawab nahi mila. Dobara puchhte hain.",
            "marathi" to "Uttara milala nahi. Punha prayatna karto."
        ),
        "EMERGENCY_TRIGGERED" to mapOf(
            "english" to "Triggering emergency alert.",
            "hinglish" to "Emergency alert bhej rahe hain.",
            "marathi" to "Emergency alert pathavat aahe."
        ),
        "TAKE_CARE" to mapOf(
            "english" to "Please take care of yourself.",
            "hinglish" to "Kripya apna dhyan rakhiye.",
            "marathi" to "Krupaya swataachi kaaljee ghya."
        )
    )
    
    // Positive response keywords for recognition
    private val positiveResponses = mapOf(
        "english" to listOf(
            "yes", "yeah", "yep", "fine", "okay", "ok", "alright", "all right", 
            "good", "better", "i am", "i'm", "better now", "hmm"
        ),
        "hinglish" to listOf(
            "हां", "हा", "ha", "haa", "ji", "jee", "theek", "thik", "hoon", "hu", 
            "हूँ", "ठीक", "थीक", "हून", "जी"
        ),
        "marathi" to listOf(
            "हो", "ho", "होय", "hoy", "बरं", "bara", "ठीक", "thik", "आहे", "ahe", 
            "बरा", "bara", "मी", "mi"
        )
    )
    
    // Speech recognition states
    enum class ConversationState {
        IDLE,
        FALL_CONFIRMATION,
        GENERAL_LISTENING
    }
    
    companion object {
        // Singleton instance
        @Volatile
        private var instance: VoiceAssistant? = null
        
        fun getInstance(context: Context): VoiceAssistant {
            return instance ?: synchronized(this) {
                instance ?: VoiceAssistant(context.applicationContext).also { instance = it }
            }
        }
    }
    
    init {
        loadPreferences()
        initializeTTS()
        initializeSpeechRecognizer()
        // Install bundled audio files if needed
        audioManager.installBundledAudioFiles()
    }
    
    /**
     * Initialize the Text-to-Speech engine
     */
    private fun initializeTTS() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = when (currentLanguage) {
                    "english" -> Locale.US
                    "hinglish" -> Locale("hi", "IN")
                    "marathi" -> Locale("mr", "IN")
                    else -> Locale.US
                }
                
                val result = textToSpeech?.setLanguage(locale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Language not supported: $currentLanguage, using English instead")
                    textToSpeech?.setLanguage(Locale.US)
                }
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
            }
        }
    }
    
    /**
     * Load user preferences for language and voice settings
     */
    fun loadPreferences() {
        currentLanguage = Settings.getPreferredLanguage(context)
        useTTS = Settings.useTTS(context)
        
        // Re-initialize TTS if language changed
        initializeTTS()
    }
    
    /**
     * Initialize or re-initialize the speech recognizer
     */
    private fun initializeSpeechRecognizer() {
        // Release existing resources
        speechRecognizer?.destroy()
        
        // Create new speech recognizer
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(createRecognitionListener())
        } else {
            Log.e(TAG, "Speech recognition not available on this device")
        }
    }
    
    /**
     * Create a recognition listener for speech recognition
     */
    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "Ready for speech")
                // Cancel any pending timeouts
                cancelTimeout()
                
                // Start a new timeout
                startTimeout()
            }
            
            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Beginning of speech")
            }
            
            override fun onRmsChanged(rmsdB: Float) {
                // Used for visualization of voice input, not needed here
            }
            
            override fun onBufferReceived(buffer: ByteArray?) {
                // Not needed for basic functionality
            }
            
            override fun onEndOfSpeech() {
                Log.d(TAG, "End of speech")
                cancelTimeout()
            }
            
            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Unknown error"
                }
                
                Log.e(TAG, "Error in speech recognition: $errorMessage")
                
                // For certain errors, retry
                if (error == SpeechRecognizer.ERROR_NETWORK || 
                    error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {
                    mainHandler.postDelayed({
                        when (currentConversationState) {
                            ConversationState.FALL_CONFIRMATION -> startFallConfirmation(fallConfirmationCallback)
                            ConversationState.GENERAL_LISTENING -> startListening(generalResultCallback)
                            else -> {} // Do nothing in IDLE state
                        }
                    }, 1000) // Retry after 1 second
                } else {
                    // Handle specific states
                    when (currentConversationState) {
                        ConversationState.FALL_CONFIRMATION -> {
                            // Default to sending emergency alert if we can't get confirmation
                            fallConfirmationCallback?.invoke(false)
                        }
                        ConversationState.GENERAL_LISTENING -> {
                            generalResultCallback?.invoke(null)
                        }
                        else -> {} // Do nothing in IDLE state
                    }
                    
                    // Reset state
                    currentConversationState = ConversationState.IDLE
                }
                
                cancelTimeout()
            }
            
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                Log.d(TAG, "Speech recognition results: $matches")
                
                if (!matches.isNullOrEmpty()) {
                    pendingResults = matches
                    
                    when (currentConversationState) {
                        ConversationState.FALL_CONFIRMATION -> processFallConfirmationResponse(matches)
                        ConversationState.GENERAL_LISTENING -> processGeneralResponse(matches)
                        else -> {}
                    }
                } else {
                    // No results, handle as error
                    when (currentConversationState) {
                        ConversationState.FALL_CONFIRMATION -> fallConfirmationCallback?.invoke(false)
                        ConversationState.GENERAL_LISTENING -> generalResultCallback?.invoke(null)
                        else -> {}
                    }
                }
                
                // Reset state
                currentConversationState = ConversationState.IDLE
                cancelTimeout()
            }
            
            override fun onPartialResults(partialResults: Bundle?) {
                // Not needed for basic functionality
            }
            
            override fun onEvent(eventType: Int, params: Bundle?) {
                // Not needed for basic functionality
            }
        }
    }
    
    /**
     * Process the voice response for fall confirmation
     */
    private fun processFallConfirmationResponse(matches: ArrayList<String>) {
        val language = getPreferredLanguage()
        
        // Expanded list of positive responses in different languages
        val positiveResponses = when (language) {
            "hinglish" -> listOf(
                "yes", "haan", "han", "ha", "thik hai", "ok", "okay", "mai thik hu", 
                "thik hu", "mai thik hoon", "bilkul", "haa ji", "ji haa", "theek"
            )
            "marathi" -> listOf(
                "ho", "hoy", "hoय", "barobar", "thik ahe", "ho barobar", "mi thik ahe",
                "bara ahe", "mala kahi jhala nahi", "mi padlo nahi", "kahi nahi", "bara"
            )
            else -> listOf(
                "yes", "yeah", "ok", "okay", "i'm okay", "i am fine", "fine", "alright",
                "all right", "i'm good", "good", "nothing happened", "not hurt", "safe", 
                "i'm safe", "not injured", "i can get up", "better", "better now"
            )
        }
        
        // Check all received matches, not just the first one
        var userIsOkay = false
        
        // Log all responses for debugging
        Log.d(TAG, "Processing fall confirmation responses: $matches")
        
        // Process all matches
        for (match in matches) {
            val lowerResponse = match.lowercase()
            
            // First try direct matches
            if (positiveResponses.any { lowerResponse.contains(it) }) {
                userIsOkay = true
                Log.d(TAG, "User confirmed they are okay with phrase: $lowerResponse")
                break
            }
            
            // Check for variations and combined phrases
            val words = lowerResponse.split(" ", ",", ".", "?", "!")
            for (word in words) {
                if (word.length > 2 && positiveResponses.any { it.contains(word) }) {
                    // Found a partial match in a word that's at least 3 characters
                    userIsOkay = true
                    Log.d(TAG, "User confirmed they are okay with word: $word in '$lowerResponse'")
                    break
                }
            }
            
            if (userIsOkay) break
        }
        
        // Double-check for negation words that might reverse the meaning
        if (userIsOkay) {
            val negationWords = when (language) {
                "hinglish" -> listOf("nahi", "na", "nai", "mat", "no")
                "marathi" -> listOf("nahi", "nako", "no")
                else -> listOf("no", "not", "don't", "cant", "can't", "won't", "cannot")
            }
            
            // If a negation word is found close to a positive word, flip the result
            for (match in matches) {
                val lowerResponse = match.lowercase()
                if (negationWords.any { lowerResponse.contains(it) }) {
                    // Check if this is likely a negation of being okay
                    if (lowerResponse.contains("not ok") || 
                        lowerResponse.contains("not fine") || 
                        lowerResponse.contains("not good") ||
                        lowerResponse.contains("not alright")) {
                        userIsOkay = false
                        Log.d(TAG, "Detected negation in response: $lowerResponse")
                        break
                    }
                }
            }
        }
        
        // Play confirmation sound
        playAudioPhrase("CONFIRMATION_RECEIVED", language)
        
        // Log the result for debugging
        Log.d(TAG, "Final confirmation result: user is ${if (userIsOkay) "OKAY" else "NOT OKAY"}")
        
        // Notify callback
        fallConfirmationCallback?.invoke(userIsOkay)
    }
    
    /**
     * Process general voice responses
     */
    private fun processGeneralResponse(matches: ArrayList<String>) {
        val response = matches[0]
        generalResultCallback?.invoke(response)
    }
    
    /**
     * Start voice confirmation after fall detection
     */
    fun startFallConfirmation(callback: ((Boolean) -> Unit)?) {
        fallConfirmationCallback = callback
        currentConversationState = ConversationState.FALL_CONFIRMATION
        
        // Play fall detected message in preferred language
        playAudioPhrase("FALL_DETECTED", currentLanguage) {
            // After playing audio, start listening
            startSpeechRecognition()
        }
    }
    
    /**
     * Start general listening for voice commands
     */
    fun startListening(callback: ((String?) -> Unit)?) {
        generalResultCallback = callback
        currentConversationState = ConversationState.GENERAL_LISTENING
        
        // Start listening directly
        startSpeechRecognition()
    }
    
    /**
     * Stop all voice assistant operations
     */
    fun stopListening() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        mediaPlayer?.release()
        mediaPlayer = null
        cancelTimeout()
        
        // Reset state
        currentConversationState = ConversationState.IDLE
        fallConfirmationCallback = null
        generalResultCallback = null
    }
    
    /**
     * Start a new speech recognition session
     */
    private fun startSpeechRecognition() {
        if (speechRecognizer == null) {
            initializeSpeechRecognizer()
        }
        
        speechRecognizer?.let {
            val language = getPreferredLanguage()
            val locale = when (language) {
                "hinglish" -> "hi-IN" // Hindi (India)
                "marathi" -> "mr-IN" // Marathi (India)
                else -> "en-US" // Default to US English
            }
            
            val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            
            try {
                it.startListening(recognizerIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognition: ${e.message}")
            }
        }
    }
    
    /**
     * Play an audio phrase from resources or filesystem
     */
    private fun playAudioPhrase(phraseKey: String, language: String, onCompletion: (() -> Unit)? = null) {
        stopAudio()
        
        // Get the text for the phrase
        val text = phrases[phraseKey]?.get(language) ?: phrases[phraseKey]?.get("english") ?: phraseKey
        
        // If TTS is preferred, use it directly
        if (useTTS) {
            speakWithTTS(text, onCompletion)
            return
        }
        
        val audioFile = VoiceAudioManager.getLanguageAudioFile(context, phraseKey, language)
        
        // Check if the audio file exists and has content
        if (audioFile.exists() && audioFile.length() > 0) {
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(audioFile.absolutePath)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .build()
                    )
                    setOnCompletionListener {
                        it.release()
                        mediaPlayer = null
                        onCompletion?.invoke()
                    }
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing audio: ${e.message}")
                // Fall back to TTS
                speakWithTTS(text, onCompletion)
            }
        } else {
            Log.w(TAG, "Audio file not found or empty: $audioFile")
            // Fall back to TTS
            speakWithTTS(text, onCompletion)
        }
    }
    
    /**
     * Use Text-to-Speech to speak a phrase
     */
    private fun speakWithTTS(text: String, onCompletion: (() -> Unit)?) {
        if (textToSpeech?.isSpeaking == true) {
            textToSpeech?.stop()
        }
        
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val utteranceId = "utterance_${System.currentTimeMillis()}"
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } else {
            @Suppress("DEPRECATION")
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null)
        }
        
        // Call onCompletion after a delay since TTS doesn't reliably provide completion callbacks
        mainHandler.postDelayed({
            onCompletion?.invoke()
        }, 2000) // Wait 2 seconds before continuing
    }
    
    /**
     * Stop any currently playing audio
     */
    private fun stopAudio() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
    }
    
    /**
     * Start a timeout for speech recognition
     */
    private fun startTimeout() {
        cancelTimeout()
        
        timeoutRunnable = Runnable {
            Log.d(TAG, "Speech recognition timed out")
            speechRecognizer?.cancel()
            
            when (currentConversationState) {
                ConversationState.FALL_CONFIRMATION -> {
                    // Default to sending emergency alert on timeout
                    fallConfirmationCallback?.invoke(false)
                }
                ConversationState.GENERAL_LISTENING -> {
                    generalResultCallback?.invoke(null)
                }
                else -> {}
            }
            
            // Reset state
            currentConversationState = ConversationState.IDLE
        }
        
        mainHandler.postDelayed(timeoutRunnable!!, RECOGNITION_TIMEOUT_MS)
    }
    
    /**
     * Cancel any pending timeout
     */
    private fun cancelTimeout() {
        timeoutRunnable?.let {
            mainHandler.removeCallbacks(it)
        }
        timeoutRunnable = null
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        mediaPlayer?.release()
        mediaPlayer = null
        
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        
        executorService.shutdown()
    }
    
    /**
     * Get the preferred language for voice recognition from settings
     */
    private fun getPreferredLanguage(): String {
        return Settings.getPreferredLanguage(context)
    }
    
    /**
     * Set the preferred language
     */
    fun setLanguage(language: String) {
        currentLanguage = language
        // Update preferences
        Settings.setPreferredLanguage(context, language)
    }
} 