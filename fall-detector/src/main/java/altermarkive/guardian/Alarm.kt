package altermarkive.guardian

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build

class Alarm private constructor(val context: Guardian) {
    private var pool: SoundPool
    private var id: Int

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val audioAttributes: AudioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()
            pool = SoundPool.Builder()
                .setMaxStreams(5)
                .setAudioAttributes(audioAttributes)
                .build()
        } else {
            @Suppress("DEPRECATION")
            pool = SoundPool(5, AudioManager.STREAM_ALARM, 0)
        }
        id = pool.load(context.applicationContext, R.raw.alarm, 1)
    }

    companion object {
        private var singleton: Alarm? = null

        internal fun instance(context: Guardian): Alarm {
            var singleton = this.singleton
            if (singleton == null) {
                singleton = Alarm(context)
                this.singleton = singleton
            }
            return singleton
        }

        internal fun siren(context: Context) {
            loudest(context, AudioManager.STREAM_ALARM)
            val singleton = this.singleton
            if (singleton != null) {
                val pool = singleton.pool
                pool.play(singleton.id, 1.0f, 1.0f, 1, 3, 1.0f)
            }
        }
        
        // Single tone version for the voice confirmation
        internal fun singleTone(context: Context) {
            loudest(context, AudioManager.STREAM_ALARM)
            val singleton = this.singleton
            if (singleton != null) {
                val pool = singleton.pool
                pool.play(singleton.id, 1.0f, 1.0f, 1, 0, 1.0f)
            }
        }

        internal fun loudest(context: Context, stream: Int) {
            val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val loudest = manager.getStreamMaxVolume(stream)
            manager.setStreamVolume(stream, loudest, 0)
        }

        internal fun alert(context: Context, emergencyNumber: String) {
            Guardian.say(
                context,
                android.util.Log.WARN,
                TAG,
                "Alerting the provided emergency phone number ($emergencyNumber)"
            )
            // Create a more detailed message
            val message = "EMERGENCY ALERT: A fall has been detected by Guardian Fall Detector By TriNetra. " + "Location: https://maps.app.goo.gl/ysg4p3jPEm3Xc6BZ6"
                    "The user was unable to confirm they are okay. Please check on them immediately. " +
                    "आपातकालीन अलर्ट: गिरने का पता चला है। उपयोगकर्ता यह पुष्टि करने में असमर्थ थे कि वे ठीक हैं। कृपया तुरंत उनकी जाँच करें।"
            
            Messenger.sms(context, emergencyNumber, message)
            Telephony.call(context, emergencyNumber)
        }

        internal fun alert(context: Context) {
            val contact = Contact[context]
            if (contact != null && "" != contact) {
                Guardian.say(
                    context,
                    android.util.Log.WARN,
                    TAG,
                    "Alerting the emergency phone number ($contact)"
                )
                // Create a more detailed message
                val message = "EMERGENCY ALERT: A fall has been detected by Guardian Fall Detector. " +
                        "The user was unable to confirm they are okay. Please check on them immediately. " +
                        "आपातकालीन अलर्ट: गिरने का पता चला है। उपयोगकर्ता यह पुष्टि करने में असमर्थ थे कि वे ठीक हैं। कृपया तुरंत उनकी जाँच करें।"
                
                Messenger.sms(context, contact, message)
                Telephony.call(context, contact)
            } else {
                Guardian.say(context, android.util.Log.ERROR, TAG, "ERROR: Emergency phone number not set")
                siren(context)
            }
        }

        private val TAG: String = Alarm::class.java.simpleName
    }
}