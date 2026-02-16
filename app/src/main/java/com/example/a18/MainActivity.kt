package com.example.lecturemotparmotapp

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.navigation.compose.rememberNavController
import com.example.a18.FullscreenManager
import planner.AppNavHost
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
    private val fullscreenManager by lazy { FullscreenManager(this) }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        // 1) Demander la permission de notifications (Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

// 2) Replanifier les alarmes pour le calendrier principal au démarrage
        lifecycleScope.launchWhenStarted {
            val mainId = Notifications.MainCalendarStore.mainIdFlow(applicationContext).first()
            if (!mainId.isNullOrBlank()) {
                val sc = Calendars.CalendarRepository.load(applicationContext, mainId)
                val items = sc?.items ?: emptyList()
                Notifications.EventAlarmScheduler.scheduleFor(applicationContext, items)
            }
        }

        setContent {
            val navController = androidx.navigation.compose.rememberNavController()
            val navigateTo = intent?.getStringExtra("navigate_to")
            // Garantir que la navigation se fait après installation du graph
            androidx.compose.runtime.LaunchedEffect(navigateTo) {
                if (navigateTo == your.pkg.ui.navigation.Routes.NOTIFICATIONS) {
                    navController.navigate(your.pkg.ui.navigation.Routes.NOTIFICATIONS) {
                        launchSingleTop = true
                    }
                    // Eviter recomposition / boucle infinie :
                    intent?.removeExtra("navigate_to")
                }
            }
            MaterialTheme {
                AppNavHost(navController, fullscreenManager)  // ← lance Home → Planner → Calendar
            }
        }
    }
}

