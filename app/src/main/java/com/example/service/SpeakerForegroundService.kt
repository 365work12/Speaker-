package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity

class SpeakerForegroundService : Service() {

    companion object {
        private const val TAG = "SpeakerFgService"
        const val CHANNEL_ID = "soundlink_speaker_service_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.action.START"
        const val ACTION_STOP = "com.example.action.STOP"
        const val ACTION_PLAY = "com.example.action.PLAY"
        const val ACTION_PAUSE = "com.example.action.PAUSE"
        const val ACTION_TOGGLE_PLAY_PAUSE = "com.example.action.TOGGLE_PLAY_PAUSE"

        const val EXTRA_STATUS = "extra_status"
        const val EXTRA_SUBTEXT = "extra_subtext"
        const val EXTRA_IS_PLAYING = "extra_is_playing"

        var isRunning = false
            private set

        var isPlaybackActive: Boolean = true
            private set

        var currentStatus: String = "Speaker active"
            private set

        var currentSubtext: String = "SoundLink audio service is active"
            private set

        // Callbacks connected to SpeakerViewModel / AudioPlaybackManager
        var onTogglePlayPause: ((Boolean) -> Unit)? = null
        var onStopAction: (() -> Unit)? = null

        fun startService(
            context: Context,
            status: String,
            subtext: String,
            isPlaying: Boolean = true
        ) {
            try {
                currentStatus = status
                currentSubtext = subtext
                isPlaybackActive = isPlaying

                val intent = Intent(context, SpeakerForegroundService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_STATUS, status)
                    putExtra(EXTRA_SUBTEXT, subtext)
                    putExtra(EXTRA_IS_PLAYING, isPlaying)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start foreground service: ${e.message}")
            }
        }

        fun updatePlaybackState(
            context: Context,
            isPlaying: Boolean,
            status: String? = null,
            subtext: String? = null
        ) {
            if (!isRunning) return
            try {
                isPlaybackActive = isPlaying
                status?.let { currentStatus = it }
                subtext?.let { currentSubtext = it }

                val intent = Intent(context, SpeakerForegroundService::class.java).apply {
                    action = if (isPlaying) ACTION_PLAY else ACTION_PAUSE
                    putExtra(EXTRA_STATUS, currentStatus)
                    putExtra(EXTRA_SUBTEXT, currentSubtext)
                    putExtra(EXTRA_IS_PLAYING, isPlaying)
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update playback state: ${e.message}")
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, SpeakerForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop foreground service: ${e.message}")
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()

        // startForeground() must be invoked immediately inside onCreate()
        // before any other operations to strictly satisfy Android's foreground service contract
        try {
            val initialNotification = buildNotification(currentStatus, currentSubtext, isPlaybackActive)
            val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                initialNotification,
                foregroundServiceType
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service in onCreate", e)
        }

        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.d(TAG, "Notification STOP action clicked")
                onStopAction?.invoke()
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                } catch (_: Exception) {}
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_PLAY -> {
                Log.d(TAG, "Notification PLAY/RESUME action clicked")
                isPlaybackActive = true
                onTogglePlayPause?.invoke(true)
            }

            ACTION_PAUSE -> {
                Log.d(TAG, "Notification PAUSE action clicked")
                isPlaybackActive = false
                onTogglePlayPause?.invoke(false)
            }

            ACTION_TOGGLE_PLAY_PAUSE -> {
                isPlaybackActive = !isPlaybackActive
                Log.d(TAG, "Notification TOGGLE action clicked, now: $isPlaybackActive")
                onTogglePlayPause?.invoke(isPlaybackActive)
            }
        }

        intent?.getStringExtra(EXTRA_STATUS)?.let { currentStatus = it }
        intent?.getStringExtra(EXTRA_SUBTEXT)?.let { currentSubtext = it }
        if (intent?.hasExtra(EXTRA_IS_PLAYING) == true) {
            isPlaybackActive = intent.getBooleanExtra(EXTRA_IS_PLAYING, true)
        }

        try {
            val notification = buildNotification(currentStatus, currentSubtext, isPlaybackActive)
            val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                foregroundServiceType
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error updating foreground notification", e)
        }

        return START_NOT_STICKY
    }

    private fun buildNotification(status: String, subtext: String, isPlaying: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Play / Pause PendingIntent
        val playPauseIntent = Intent(this, SpeakerForegroundService::class.java).apply {
            action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        }
        val playPausePendingIntent = PendingIntent.getService(
            this, 2, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop PendingIntent
        val stopIntent = Intent(this, SpeakerForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying) "Pause" else "Resume"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle("SoundLink: $status")
            .setContentText(subtext)
            .setContentIntent(contentPendingIntent)
            .addAction(playPauseIcon, playPauseTitle, playPausePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SoundLink Active Speaker Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps audio receiver and background playback active when using other apps"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun acquireLocks() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SoundLink::AudioWakeLock").apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L) // 12 hours max
            }

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "SoundLink::WifiLock").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {}
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            wakeLock = null
            if (wifiLock?.isHeld == true) wifiLock?.release()
            wifiLock = null
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        isRunning = false
        releaseLocks()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
