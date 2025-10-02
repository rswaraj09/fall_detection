package altermarkive.guardian

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager

/**
 * Settings fragment for the application
 */
class SettingsFragment : PreferenceFragmentCompat() {
    companion object {
        private const val TAG = "SettingsFragment"
    }
    
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        
        // Set up language preference
        val languagePref = findPreference<ListPreference>("voice_language")
        languagePref?.setOnPreferenceChangeListener { _, newValue ->
            val language = newValue as String
            Log.d(TAG, "Language changed to: $language")
            
            // Update the voice assistant language
            VoiceAssistant.getInstance(requireContext()).setLanguage(language)
            true
        }
    }
}

/**
 * Utility class for accessing common settings
 */
class Settings {
    companion object {
        /**
         * Get the preferred language for voice interactions
         */
        fun getPreferredLanguage(context: Context): String {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            // Check both new and old preference keys to maintain compatibility
            return prefs.getString("voice_language", null) 
                ?: prefs.getString("language_preference", "english") 
                ?: "english"
        }
        
        /**
         * Set the preferred language for voice interactions
         */
        fun setPreferredLanguage(context: Context, language: String) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            prefs.edit()
                .putString("voice_language", language)
                .putString("language_preference", language) // For backwards compatibility
                .apply()
        }
        
        /**
         * Check if voice confirmation is enabled
         */
        fun isVoiceConfirmationEnabled(context: Context): Boolean {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getBoolean("enable_voice_confirmation", true)
        }
        
        /**
         * Enable or disable voice confirmation
         */
        fun setVoiceConfirmationEnabled(context: Context, enabled: Boolean) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            prefs.edit().putBoolean("enable_voice_confirmation", enabled).apply()
        }
        
        /**
         * Check if TTS is enabled instead of pre-recorded audio
         */
        fun useTTS(context: Context): Boolean {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getBoolean("use_tts", false) || prefs.getBoolean("use_tts_preference", false)
        }
    }
}