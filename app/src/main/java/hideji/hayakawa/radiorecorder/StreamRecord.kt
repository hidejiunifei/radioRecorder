package hideji.hayakawa.radiorecorder

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.*
import java.net.URL
import java.time.LocalDateTime

enum class Actions
{
    START,
    STOP
}
class StreamRecord : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceStarted = false
    private var isRecording = false
    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val action = intent.action
            when (action) {
                Actions.START.name -> startService()
                Actions.STOP.name -> stopService()
            }
        }

        return START_STICKY
    }

    private fun startService() {
        if (isServiceStarted) return
        Toast.makeText(this, "Service starting its task", Toast.LENGTH_SHORT).show()
        isServiceStarted = true
        setServiceState(this, ServiceState.STARTED)

        // we need this lock so our service gets not affected by Doze Mode
        wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RecordStream::lock").apply {
                    acquire()
                }
            }

            GlobalScope.launch(Dispatchers.IO) {
                while (isServiceStarted) {
                    if (!isRecording)
                    {
                        isRecording = true
                        launch(Dispatchers.IO) {
                            RecordStream()
                        }
                    }
                    delay(60 * 1 *1000)
                }
            }
        }

    private fun stopService() {
        Toast.makeText(this, "Service stopping", Toast.LENGTH_SHORT).show()
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            stopForeground(true)
            stopSelf()
        } catch (e: Exception) {
        }
        isServiceStarted = false
        isRecording = false
        setServiceState(this, ServiceState.STOPPED)
    }

    override fun onCreate() {
        super.onCreate()
        var notification = createNotification()
        startForeground(1, notification)
    }

    private fun createNotification(): Notification {
        val notificationChannelId = "RADIO RECORDER SERVICE CHANNEL"

        // depending on the Android API that we're dealing with we will have
        // to use a specific method to create the notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;
            val channel = NotificationChannel(
                notificationChannelId,
                "Radio recorder Service notifications channel",
                NotificationManager.IMPORTANCE_HIGH
            ).let {
                it.description = "Radio recorder Service channel"
                it.enableLights(true)
                it.lightColor = Color.RED
                it.enableVibration(true)
                it.vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
                it
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, 0)
        }

        val builder: Notification.Builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(
            this,
            notificationChannelId
        ) else Notification.Builder(this)

        return builder
            .setContentTitle("Radio recorder Service")
            .setContentText("Radio recorder service working")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setTicker("Ticker text")
            .setPriority(Notification.PRIORITY_HIGH) // for under android 26 compatibility
            .build()
    }

    private fun RecordStream()
    {
        var currentDateTime = LocalDateTime.now()

        if (currentDateTime.hour in 7..11 || currentDateTime.hour in 14..23)
        {
            if (currentDateTime.minute > 49 || currentDateTime.minute < 15) {
                try {
                    val inputStream: InputStream =
                            URL("https://sc4s.cdn.upx.com:8036/stream.mp3").openStream()

                    var filename = "${Environment.getExternalStorageDirectory().path}/jovempan/" +
                            "${currentDateTime.year}-${currentDateTime.monthValue}-${currentDateTime.dayOfMonth}-" +
                            "${currentDateTime.hour}.mp3"
                    val outputSource = File(filename)
                    val outputStream: OutputStream = FileOutputStream(outputSource, true)
                    var bytesRead: Int
                    val buffer = ByteArray(10 * 1024)
                    while (inputStream.read(buffer).also { bytesRead = it } > 0 && (currentDateTime.minute > 49 || currentDateTime.minute < 15)) {
                        outputStream.write(buffer, 0, bytesRead)
                        currentDateTime = LocalDateTime.now()
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
        isRecording = false
    }
}
