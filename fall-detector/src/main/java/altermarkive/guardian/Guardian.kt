package altermarkive.guardian

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log as AndroidLog
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat

class Guardian : Service() {
    private val TAG = "Guardian"
    
    override fun onCreate() {
        try {
            AndroidLog.d(TAG, "Guardian service onCreate started")
            
            try {
                Positioning.initiate(this)
                AndroidLog.d(TAG, "Positioning initiated")
            } catch (e: Exception) {
                AndroidLog.e(TAG, "Error initiating Positioning: ${e.message}")
                e.printStackTrace()
            }
            
            try {
                Detector.instance(this)
                AndroidLog.d(TAG, "Detector initiated")
            } catch (e: Exception) {
                AndroidLog.e(TAG, "Error initiating Detector: ${e.message}")
                e.printStackTrace()
            }
            
            try {
                Sampler.instance(this)
                AndroidLog.d(TAG, "Sampler initiated")
            } catch (e: Exception) {
                AndroidLog.e(TAG, "Error initiating Sampler: ${e.message}")
                e.printStackTrace()
            }
            
            try {
                Alarm.instance(this)
                AndroidLog.d(TAG, "Alarm initiated")
            } catch (e: Exception) {
                AndroidLog.e(TAG, "Error initiating Alarm: ${e.message}")
                e.printStackTrace()
            }
            
            AndroidLog.d(TAG, "Guardian service onCreate completed")
        } catch (e: Exception) {
            AndroidLog.e(TAG, "Fatal error in onCreate: ${e.message}")
            e.printStackTrace()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = resources.getString(R.string.app)
        val channelName = "$channelId Background Service"
        val channel = NotificationChannel(
            channelId,
            channelName, NotificationManager.IMPORTANCE_LOW
        )
        channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(channel)
        return channelId
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    override fun onStartCommand(intent: Intent?, flags: Int, startID: Int): Int {
        AndroidLog.d(TAG, "Guardian service onStartCommand")
        
        try {
            val now = System.currentTimeMillis()
            val app = resources.getString(R.string.app)
            val channelId =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    createNotificationChannel()
                } else {
                    // If earlier version channel ID is not used
                    ""
                }
                
            val mainIntent = Intent(this, Main::class.java)
            val pendingFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            
            val pending = PendingIntent.getActivity(this, 0, mainIntent, pendingFlag)
            
            val notification = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(app)
                .setContentText("$app is active and monitoring for falls")
                .setWhen(now)
                .setContentIntent(pending)
                .build()
                
            startForeground(1, notification)
            AndroidLog.d(TAG, "Guardian service started in foreground")
            
        } catch (e: Exception) {
            AndroidLog.e(TAG, "Error in onStartCommand: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, "Error starting fall detection service: ${e.message}", Toast.LENGTH_LONG).show()
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    companion object {
        private val TAG = "Guardian"
        
        internal fun initiate(context: Context) {
            try {
                AndroidLog.d(TAG, "Initiating Guardian service")
                val intent = Intent(context, Guardian::class.java)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                    AndroidLog.d(TAG, "Started as foreground service")
                } else {
                    context.startService(intent)
                    AndroidLog.d(TAG, "Started as regular service")
                }
            } catch (e: Exception) {
                AndroidLog.e(TAG, "Error initiating Guardian service: ${e.message}")
                e.printStackTrace()
                Toast.makeText(context, "Error starting fall detection: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        internal fun say(context: Context, level: Int, tag: String, message: String) {
            Log.println(level, tag, message)
            try {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                AndroidLog.e(TAG, "Error showing toast: ${e.message}")
            }
        }
    }
}