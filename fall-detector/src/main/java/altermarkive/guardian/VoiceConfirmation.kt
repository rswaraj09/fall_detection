package altermarkive.guardian

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class VoiceConfirmation {
    companion object {
        private val TAG = "VoiceConfirmation"
        private val isRunning = AtomicBoolean(false)
        private var mediaPlayer: MediaPlayer? = null
        private var textToSpeech: TextToSpeech? = null
        private var attemptCount = 0
        private const val MAX_ATTEMPTS = 2
        
        // List of positive responses to check against
        private val POSITIVE_RESPONSES = listOf(
            "हां", // haan
            "हा", // haa
            "ha", 
            "haa",
            "yes",
            "fine",
            "okay",
            "ok",
            "थीक", // theek
            "ठीक", // theek 
            "thik",
            "theek",
            "हूँ", // hoon
            "हून", // hoon
            "hoon",
            "ho",
            "आहे", // aahe
            "aahe",
            "main",
            "me",
            "i am",
            "i'm",
            "good",
            "alright",
            "all right",
            "better",
            "better now",
            "yeah",
            "yep",
            "hmm",
            "hmmmm",
            "oh yes"
        )
        
        fun startConfirmation(context: Context) {
            if (isRunning.getAndSet(true)) {
                // Already running a confirmation
                return
            }

            Log.d(TAG, "Starting voice confirmation process")
            attemptCount = 0
            
            // Start the first attempt
            startConfirmationAttempt(context)
        }
        
        private fun startConfirmationAttempt(context: Context) {
            // Increment attempt counter
            attemptCount++
            
            Log.d(TAG, "Voice confirmation attempt $attemptCount")
            
            // Play the "Kya aap theek hai" prompt
            playPrompt(context)
            
            // After playing the prompt, start listening for a response
            Handler(Looper.getMainLooper()).postDelayed({
                listenForResponse(context)
            }, 3000) // Give 3 seconds for the prompt to play
        }
        
        private fun playPrompt(context: Context) {
            try {
                // Release any existing MediaPlayer
                mediaPlayer?.release()
                
                try {
                    // First try to use the audio file if it exists
                    val resId = context.resources.getIdentifier("kya_aap_theek_hai", "raw", context.packageName)
                    if (resId != 0) {
                        mediaPlayer = MediaPlayer.create(context, resId)
                        mediaPlayer?.setOnCompletionListener {
                            it.release()
                        }
                        mediaPlayer?.start()
                    } else {
                        // If audio file doesn't exist, use Text-to-Speech as fallback
                        useTextToSpeech(context, "Kya aap theek hai?")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error playing audio file: ${e.message}")
                    // Fall back to text-to-speech
                    useTextToSpeech(context, "Kya aap theek hai?")
                }
                
                // Show toast with the message (as visual feedback)
                Toast.makeText(context, "KYA AAP THEEK HAI? (Attempt $attemptCount/$MAX_ATTEMPTS)", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e(TAG, "Error playing prompt: ${e.message}")
                // If there's an error with voice, use a single tone from the alarm sound
                try {
                    Alarm.singleTone(context)
                    Toast.makeText(context, "KYA AAP THEEK HAI? (Attempt $attemptCount/$MAX_ATTEMPTS)", Toast.LENGTH_LONG).show()
                } catch (e2: Exception) {
                    Log.e(TAG, "Error playing alarm tone: ${e2.message}")
                    // All audio methods failed, trigger the emergency
                    triggerEmergency(context)
                }
            }
        }
        
        private fun speakConfirmationReceived(context: Context) {
            try {
                // Play confirmation message "Aapke confirmation milla hume" followed by "Take care"
                try {
                    // First try to use an audio file if it exists
                    val resId = context.resources.getIdentifier("confirmation_received", "raw", context.packageName)
                    if (resId != 0) {
                        mediaPlayer = MediaPlayer.create(context, resId)
                        mediaPlayer?.setOnCompletionListener {
                            it.release()
                            // After confirmation message, play the take care message
                            speakTakeCare(context)
                        }
                        mediaPlayer?.start()
                    } else {
                        // If audio file doesn't exist, use Text-to-Speech
                        useTextToSpeech(context, "Aapke confirmation milla hume. Apna dhyan rakhiye.", true)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error playing confirmation audio: ${e.message}")
                    // Fall back to text-to-speech
                    useTextToSpeech(context, "Aapke confirmation milla hume. Apna dhyan rakhiye.", true)
                }
                
                // Show toast with the confirmation message
                Toast.makeText(context, "AAPKE CONFIRMATION MILLA HUME. APNA DHYAN RAKHIYE.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e(TAG, "Error speaking confirmation: ${e.message}")
                // If there's an error, just reset the running flag
                isRunning.set(false)
            }
        }
        
        private fun speakTakeCare(context: Context) {
            try {
                // Try to use a dedicated take care audio file if it exists
                val resId = context.resources.getIdentifier("take_care", "raw", context.packageName)
                if (resId != 0) {
                    mediaPlayer = MediaPlayer.create(context, resId)
                    mediaPlayer?.setOnCompletionListener {
                        it.release()
                        isRunning.set(false)
                    }
                    mediaPlayer?.start()
                } else {
                    // Reset the running flag since we're done with the sequence
                    isRunning.set(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing take care audio: ${e.message}")
                isRunning.set(false)
            }
        }
        
        private fun useTextToSpeech(context: Context, message: String, isConfirmation: Boolean = false) {
            // Clean up any existing TTS instance
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            
            // Create a new TTS instance
            textToSpeech = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    // Set language to Hindi
                    val result = textToSpeech?.setLanguage(Locale("hi", "IN"))
                    
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        // If Hindi not available, try English
                        textToSpeech?.setLanguage(Locale.ENGLISH)
                    }
                    
                    // Set the utterance completed listener for older Android versions
                    if (isConfirmation) {
                        @Suppress("DEPRECATION")
                        textToSpeech?.setOnUtteranceCompletedListener { _ ->
                            // Release TTS resources when done
                            textToSpeech?.stop()
                            textToSpeech?.shutdown()
                            textToSpeech = null
                            
                            // Reset running flag
                            isRunning.set(false)
                        }
                    }
                    
                    // Create parameters for the utterance
                    @Suppress("DEPRECATION")
                    val params = HashMap<String, String>()
                    params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = if (isConfirmation) "confirmation_id" else "prompt_id"
                    
                    // Use the deprecated version of speak for compatibility
                    @Suppress("DEPRECATION")
                    textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, params)
                } else {
                    Log.e(TAG, "TextToSpeech initialization failed")
                    if (isConfirmation) {
                        isRunning.set(false)
                    }
                }
            }
        }
        
        private fun listenForResponse(context: Context) {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.e(TAG, "Speech recognition not available")
                handleNoResponse(context)
                return
            }
            
            val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            
            // Use multiple languages to increase chances of recognition
            speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN,en-IN,en-US")
            
            // Increase the number of results for better matching
            speechIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 10)
            
            // Additional speech recognition settings
            speechIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            speechIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000)
            speechIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
            speechIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000)
            speechIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            
            // Show explicit messages to help with debugging
            Toast.makeText(context, "Listening for your response...", Toast.LENGTH_SHORT).show()
            
            // Increase time limit for response - 12 seconds
            val responseTimeoutHandler = Handler(Looper.getMainLooper())
            val responseTimeout = Runnable {
                speechRecognizer.destroy()
                Log.d(TAG, "No response received within timeout period")
                handleNoResponse(context)
            }
            
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Ready for speech")
                    // Set timeout for response
                    responseTimeoutHandler.postDelayed(responseTimeout, 12000)
                }
                
                override fun onResults(results: Bundle?) {
                    responseTimeoutHandler.removeCallbacks(responseTimeout)
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    
                    if (matches != null && matches.isNotEmpty()) {
                        val responses = matches.joinToString(", ") { it.lowercase(Locale.getDefault()) }
                        Log.d(TAG, "Speech recognized: $responses")
                        
                        // Check if any of the responses contain a positive confirmation
                        var isPositive = false
                        var matchedWord = ""
                        
                        // First check for exact matches
                        for (response in matches) {
                            val lowerResponse = response.lowercase(Locale.getDefault())
                            
                            // Check against our list of positive responses
                            for (positiveWord in POSITIVE_RESPONSES) {
                                if (lowerResponse == positiveWord || 
                                    lowerResponse.contains(" $positiveWord ") || 
                                    lowerResponse.startsWith("$positiveWord ") || 
                                    lowerResponse.endsWith(" $positiveWord") || 
                                    lowerResponse.contains(positiveWord)) {
                                    isPositive = true
                                    matchedWord = positiveWord
                                    Log.d(TAG, "Found positive word: $positiveWord in response: $lowerResponse")
                                    break
                                }
                            }
                            
                            if (isPositive) break
                        }
                        
                        if (isPositive) {
                            Log.d(TAG, "User confirmed they are okay with word: $matchedWord")
                            Toast.makeText(context, "Response detected: $matchedWord", Toast.LENGTH_SHORT).show()
                            
                            // User confirmed they are okay, play confirmation message
                            speechRecognizer.destroy()
                            speakConfirmationReceived(context)
                        } else {
                            // No positive confirmation, treat as no response
                            Log.d(TAG, "No positive confirmation in responses, treating as no response")
                            Toast.makeText(context, "No confirmation detected in: $responses", Toast.LENGTH_SHORT).show()
                            speechRecognizer.destroy()
                            handleNoResponse(context)
                        }
                    } else {
                        // No speech detected, handle as no response
                        Log.d(TAG, "No speech detected")
                        Toast.makeText(context, "No speech detected", Toast.LENGTH_SHORT).show()
                        speechRecognizer.destroy()
                        handleNoResponse(context)
                    }
                }
                
                override fun onPartialResults(partialResults: Bundle?) {
                    val partialMatches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (partialMatches != null && partialMatches.isNotEmpty()) {
                        val partialResponsesText = partialMatches.joinToString(", ")
                        Log.d(TAG, "Partial results: $partialResponsesText")
                    }
                }
                
                override fun onError(error: Int) {
                    responseTimeoutHandler.removeCallbacks(responseTimeout)
                    
                    // Log specific error message
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
                    
                    Log.e(TAG, "Speech recognition error: $errorMessage ($error)")
                    Toast.makeText(context, "Speech error: $errorMessage", Toast.LENGTH_SHORT).show()
                    
                    // For some errors, we want to try again immediately
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || 
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        
                        speechRecognizer.destroy()
                        handleNoResponse(context)
                    } else {
                        // For other errors, try the system's built-in speech recognition
                        trySystemSpeechRecognition(context)
                    }
                }
                
                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "Speech started")
                }
                
                override fun onEndOfSpeech() {
                    Log.d(TAG, "Speech ended")
                }
                
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            
            try {
                speechRecognizer.startListening(speechIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognition: ${e.message}")
                speechRecognizer.destroy()
                
                // Try the system's built-in speech recognition as fallback
                trySystemSpeechRecognition(context)
            }
        }
        
        private fun trySystemSpeechRecognition(context: Context) {
            try {
                // Try to use the system's speech recognition activity as a fallback
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say 'yes' if you are okay")
                intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 10)
                
                // We can't directly get results from this, so we'll have to assume no response
                Toast.makeText(context, "Speech recognition failed. Please respond verbally or press a button.", Toast.LENGTH_LONG).show()
                
                // Continue with no response
                handleNoResponse(context)
            } catch (e: Exception) {
                Log.e(TAG, "Could not use system speech recognition: ${e.message}")
                handleNoResponse(context)
            }
        }
        
        private fun handleNoResponse(context: Context) {
            if (attemptCount < MAX_ATTEMPTS) {
                // If we haven't reached the maximum number of attempts, try again
                Log.d(TAG, "No response on attempt $attemptCount, trying again")
                
                // Wait a moment before trying again
                Handler(Looper.getMainLooper()).postDelayed({
                    startConfirmationAttempt(context)
                }, 2000) // 2 second pause between attempts
            } else {
                // We've reached the maximum number of attempts, trigger emergency
                Log.d(TAG, "No response after $MAX_ATTEMPTS attempts, triggering emergency")
                triggerEmergency(context)
            }
        }
        
        private fun triggerEmergency(context: Context) {
            isRunning.set(false)
            
            // Clean up resources
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
            
            mediaPlayer?.release()
            mediaPlayer = null
            
            Log.d(TAG, "Triggering emergency alert")
            Alarm.alert(context)
        }
    }
} 