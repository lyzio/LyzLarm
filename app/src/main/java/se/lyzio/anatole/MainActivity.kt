package se.lyzio.anatole

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import se.lyzio.anatole.tracking.Tracker
import se.lyzio.anatole.ui.about.AboutScreen
import se.lyzio.anatole.ui.about.LicensesScreen
import se.lyzio.anatole.ui.alarms.EditAlarmScreen
import se.lyzio.anatole.ui.home.HomeScreen
import se.lyzio.anatole.ui.settings.AlarmDefaultsScreen
import se.lyzio.anatole.ui.settings.EmptyBedScreen
import se.lyzio.anatole.ui.settings.TrackingSettingsScreen
import se.lyzio.anatole.ui.setup.SetupFlow
import se.lyzio.anatole.ui.setup.TestAlarmScreen
import se.lyzio.anatole.ui.sleep.BedtimeScreen
import se.lyzio.anatole.ui.sleep.MorningScreen
import se.lyzio.anatole.ui.theme.AnatoleTheme
import se.lyzio.anatole.ui.theme.Lz

class MainActivity : ComponentActivity() {

    /** A pending action from a notification or the ringing screen; handled once the UI is up. */
    private val pendingAction = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Cheap safety net: make sure every enabled alarm is armed whenever the app opens.
        lifecycleScope.launch { app.scheduler.rescheduleAll() }
        if (savedInstanceState == null) pendingAction.value = intent?.action
        setContent {
            AnatoleTheme {
                Box(Modifier.fillMaxSize().background(Lz.Base)) { AppRoot(pendingAction) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingAction.value = intent.action
    }

    override fun onResume() {
        super.onResume()
        // The app is in the foreground: if a night is open but tracking died, restart it.
        lifecycleScope.launch { Tracker.resumeIfNeeded(this@MainActivity) }
    }

    companion object {
        const val ACTION_START_SLEEP = "se.lyzio.anatole.action.START_SLEEP"
        const val ACTION_OPEN_BEDTIME = "se.lyzio.anatole.action.OPEN_BEDTIME"
        const val ACTION_OPEN_MORNING = "se.lyzio.anatole.action.OPEN_MORNING"
    }
}

@Composable
private fun AppRoot(pendingAction: MutableStateFlow<String?>) {
    val context = LocalContext.current
    val settings = context.app.settings
    val onboardingDone by settings.onboardingDone.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    when (onboardingDone) {
        null -> Unit // Loading for a split second.
        false -> SetupFlow(onFinished = { scope.launch { settings.setOnboardingDone() } })
        true -> MainNav(pendingAction)
    }
}

@Composable
private fun MainNav(pendingAction: MutableStateFlow<String?>) {
    val context = LocalContext.current
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()

    fun startSleep() {
        scope.launch {
            if (Tracker.start(context) != null) {
                nav.navigate("bedtime") { launchSingleTop = true }
            } else {
                Toast.makeText(context, "Set an alarm first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val action by pendingAction.collectAsState()
    LaunchedEffect(action) {
        when (action) {
            // Bedtime reminder's "Start sleep" (#26): open Bedtime, which starts tracking once
            // the app is fully in the foreground (Android may refuse the start before that).
            MainActivity.ACTION_START_SLEEP -> nav.navigate("bedtime?start=true") { launchSingleTop = true }
            MainActivity.ACTION_OPEN_BEDTIME -> nav.navigate("bedtime") { launchSingleTop = true }
            MainActivity.ACTION_OPEN_MORNING -> nav.navigateMorning(0L)
        }
        if (action != null) pendingAction.value = null
    }

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onEditAlarm = { id -> nav.navigate("edit/$id") },
                onOpenAbout = { nav.navigate("about") },
                onRunTest = { nav.navigate("test") },
                onStartSleep = ::startSleep,
                onOpenBedtime = { nav.navigate("bedtime") { launchSingleTop = true } },
                onOpenNight = { id -> nav.navigateMorning(id) },
                onOpenTracking = { nav.navigate("settings/tracking") },
                onOpenAlarmDefaults = { nav.navigate("settings/alarm") },
                onOpenEmptyBed = { nav.navigate("emptybed") },
            )
        }
        composable("nights") {
            HomeScreen(
                onEditAlarm = { id -> nav.navigate("edit/$id") },
                onOpenAbout = { nav.navigate("about") },
                onRunTest = { nav.navigate("test") },
                onStartSleep = ::startSleep,
                onOpenBedtime = { nav.navigate("bedtime") { launchSingleTop = true } },
                onOpenNight = { id -> nav.navigateMorning(id) },
                onOpenTracking = { nav.navigate("settings/tracking") },
                onOpenAlarmDefaults = { nav.navigate("settings/alarm") },
                onOpenEmptyBed = { nav.navigate("emptybed") },
                initialTab = 1,
            )
        }
        composable(
            "edit/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            EditAlarmScreen(
                alarmId = entry.arguments?.getLong("id") ?: 0L,
                onDone = { nav.popBackStack() },
            )
        }
        composable(
            "bedtime?start={start}",
            arguments = listOf(navArgument("start") { type = NavType.BoolType; defaultValue = false }),
        ) { entry ->
            BedtimeScreen(
                startOnOpen = entry.arguments?.getBoolean("start") ?: false,
                onFinished = { id ->
                    scope.launch {
                        if (context.app.database.nightDao().night(id) != null) {
                            nav.navigate("morning/$id") { popUpTo("home") { inclusive = false } }
                        } else {
                            // Stopped within 15 minutes: not kept as a night.
                            nav.popBackStack("home", inclusive = false)
                            Toast.makeText(context, "Under 15 minutes, so not saved as a night", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onBack = { if (!nav.popBackStack()) nav.navigate("home") },
            )
        }
        composable(
            "morning/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            MorningScreen(
                nightId = entry.arguments?.getLong("id") ?: 0L,
                onBack = { if (!nav.popBackStack()) nav.navigate("home") },
                onOpenNights = {
                    nav.navigate("nights") { popUpTo("home") { inclusive = false } }
                },
            )
        }
        composable("settings/tracking") { TrackingSettingsScreen(onBack = { nav.popBackStack() }) }
        composable("settings/alarm") { AlarmDefaultsScreen(onBack = { nav.popBackStack() }) }
        composable("emptybed") { EmptyBedScreen(onBack = { nav.popBackStack() }) }
        composable("about") {
            AboutScreen(onBack = { nav.popBackStack() }, onOpenLicenses = { nav.navigate("licenses") })
        }
        composable("licenses") { LicensesScreen(onBack = { nav.popBackStack() }) }
        composable("test") {
            TestAlarmScreen(step = null, onDone = { nav.popBackStack() })
        }
    }
}

private fun NavHostController.navigateMorning(id: Long) {
    navigate("morning/$id") { launchSingleTop = true }
}
