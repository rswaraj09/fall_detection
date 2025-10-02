package altermarkive.guardian

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SmsMessage
import androidx.core.content.ContextCompat
import java.util.Locale
import android.util.Log

class Messenger : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION == action) {
            val bundle = intent.extras
            if (bundle == null) {
                Guardian.say(context, android.util.Log.WARN, TAG, "Received an SMS broadcast without extras")
            } else {
                val messages = bundle["pdus"] as Array<*>
                val message = arrayOfNulls<SmsMessage>(
                    messages.size
                )
                for (i in messages.indices) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val format = bundle.getString("format")
                        message[i] = SmsMessage.createFromPdu(messages[i] as ByteArray, format)
                    } else {
                        @Suppress("DEPRECATION")
                        message[i] = SmsMessage.createFromPdu(messages[i] as ByteArray)
                    }
                }
                if (message.isNotEmpty()) {
                    val message0 = message[0]
                    if (message0 == null) {
                        Guardian.say(context, android.util.Log.WARN, TAG, "Received an SMS without content")
                        return
                    }
                    var contact = message0.originatingAddress
                    val content = message0.messageBody.uppercase(Locale.US)
                    val items = content.split(";").toTypedArray()
                    if (items.size > 1) {
                        contact = items[1]
                    }
                    if (Contact.check(context, contact)) {
                        var prevent = false
                        if (content.contains("POSITION")) {
                            Positioning.singleton?.trigger()
                            prevent = true
                        }
                        if (content.contains("ALARM")) {
                            Alarm.siren(context)
                            prevent = true
                        }
                        if (prevent) {
                            abortBroadcast()
                        }
                    }
                } else {
                    Guardian.say(context, android.util.Log.WARN, TAG, "Received empty SMS")
                }
            }
        }
    }

    companion object {
        fun sms(context: Context, contact: String?, message: String) {
            if (contact == null || contact.isEmpty()) {
                Guardian.say(context, android.util.Log.ERROR, TAG, "ERROR: Cannot send SMS - contact number is empty")
                return
            }
            
            // Log the attempt
            Log.d(TAG, "Attempting to send SMS to $contact with message: ${message.take(50)}...")
            
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.SEND_SMS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                try {
                    val manager = SmsManager.getDefault()
                    
                    // For longer messages, divide them into parts
                    if (message.length > 160) {
                        val parts = manager.divideMessage(message)
                        manager.sendMultipartTextMessage(contact, null, parts, null, null)
                        Log.i(TAG, "Sent multipart SMS (${parts.size} parts) to $contact")
                        Guardian.say(context, android.util.Log.INFO, TAG, "Emergency SMS sent to $contact")
                    } else {
                        manager.sendTextMessage(contact, null, message, null, null)
                        Log.i(TAG, "Sent SMS to $contact")
                        Guardian.say(context, android.util.Log.INFO, TAG, "Emergency SMS sent to $contact")
                    }
                } catch (e: Exception) {
                    val errorMsg = "Failed to send SMS: ${e.message}"
                    Log.e(TAG, errorMsg, e)
                    Guardian.say(context, android.util.Log.ERROR, TAG, errorMsg)
                }
            } else {
                Guardian.say(context, android.util.Log.ERROR, TAG, "ERROR: No permission to send an SMS")
                Log.e(TAG, "Missing SMS permission! Message not sent to $contact")
            }
        }

        private val TAG: String = Messenger::class.java.simpleName
    }
}