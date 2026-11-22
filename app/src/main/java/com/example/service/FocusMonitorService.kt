package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FocusMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var monitorJob: Job? = null
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var currentSubject: String = "Lecture Session"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                removeOverlay()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                currentSubject = intent?.getStringExtra(EXTRA_SUBJECT) ?: "Class Mode"
                startForeground(NOTIFICATION_ID, buildNotification(currentSubject))
                startMonitoringLoop()
            }
        }
        return START_STICKY
    }

    private fun startMonitoringLoop() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            val safePackages = FocusPermissionHelper.getSafeListPackages(applicationContext)
            while (isActive) {
                val canMonitor = FocusPermissionHelper.hasUsageStatsPermission(applicationContext) &&
                    FocusPermissionHelper.hasOverlayPermission(applicationContext)
                if (canMonitor) {
                    val fgPackage = getForegroundPackageName()
                    if (!fgPackage.isNullOrBlank() && fgPackage !in safePackages) {
                        showOverlay(fgPackage)
                    } else if (fgPackage == packageName) {
                        removeOverlay()
                    }
                } else {
                    removeOverlay()
                }
                delay(1200L)
            }
        }
    }

    private fun getForegroundPackageName(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val end = System.currentTimeMillis()
        val begin = end - 10_000L
        val events = usm.queryEvents(begin, end) ?: return null
        val event = UsageEvents.Event()
        var lastPkg: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            ) {
                lastPkg = event.packageName
            }
        }
        return lastPkg
    }

    private fun showOverlay(blockedPackage: String) {
        if (overlayView != null) return
        if (!FocusPermissionHelper.hasOverlayPermission(this)) return

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F50F172A"))
            setPadding(72, 72, 72, 72)

            val badge = TextView(context).apply {
                text = "FOCUSGUARD • CLASS MODE"
                setTextColor(Color.parseColor("#10B981"))
                textSize = 13f
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
            }

            val title = TextView(context).apply {
                text = "App Paused During Lecture"
                setTextColor(Color.WHITE)
                textSize = 24f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, 28, 0, 16)
            }

            val detail = TextView(context).apply {
                text = "$blockedPackage is blocked while $currentSubject is in session.\n\nWhatsApp, Phone, Messages, and Settings remain available."
                setTextColor(Color.parseColor("#CBD5E1"))
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 48)
            }

            val returnBtn = Button(context).apply {
                text = "Return to StudyShield"
                setTextColor(Color.WHITE)
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#4338CA"))
                    cornerRadius = 32f
                }
                setPadding(48, 28, 48, 28)
                setOnClickListener {
                    removeOverlay()
                    val launchIntent = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    context.startActivity(launchIntent)
                }
            }

            addView(badge)
            addView(title)
            addView(detail)
            addView(returnBtn)
        }

        try {
            windowManager?.addView(root, params)
            overlayView = root
        } catch (_: Exception) {
            overlayView = null
        }
    }

    private fun removeOverlay() {
        val view = overlayView ?: return
        try {
            windowManager?.removeView(view)
        } catch (_: Exception) {
        } finally {
            overlayView = null
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FocusGuard Class Mode",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active while StudyShield Class Mode is protecting lecture focus."
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(subject: String): Notification {
        val tapIntent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Class Mode Active • $subject")
            .setContentText("Distracting apps are paused. WhatsApp, Phone & Messages stay allowed.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pending)
            .build()
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        removeOverlay()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "focusguard_class_mode"
        private const val NOTIFICATION_ID = 4102
        const val ACTION_START = "com.example.action.START_FOCUS_MONITOR"
        const val ACTION_STOP = "com.example.action.STOP_FOCUS_MONITOR"
        const val EXTRA_SUBJECT = "extra_subject"

        fun start(context: Context, subject: String) {
            val intent = Intent(context, FocusMonitorService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SUBJECT, subject)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FocusMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
        }
    }
}
