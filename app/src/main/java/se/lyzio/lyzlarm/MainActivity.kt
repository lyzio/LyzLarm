package se.lyzio.lyzlarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import se.lyzio.lyzlarm.ui.about.AboutScreen
import se.lyzio.lyzlarm.ui.about.LicensesScreen
import se.lyzio.lyzlarm.ui.alarms.EditAlarmScreen
import se.lyzio.lyzlarm.ui.home.HomeScreen
import se.lyzio.lyzlarm.ui.setup.SetupFlow
import se.lyzio.lyzlarm.ui.setup.TestAlarmScreen
import se.lyzio.lyzlarm.ui.theme.LyzLarmTheme
import se.lyzio.lyzlarm.ui.theme.Lz

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Cheap safety net: make sure every enabled alarm is armed whenever the app opens.
        lifecycleScope.launch { app.scheduler.rescheduleAll() }
        setContent {
            LyzLarmTheme {
                Box(Modifier.fillMaxSize().background(Lz.Base)) { AppRoot() }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = context.app.settings
    val onboardingDone by settings.onboardingDone.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    when (onboardingDone) {
        null -> Unit // Loading for a split second.
        false -> SetupFlow(onFinished = { scope.launch { settings.setOnboardingDone() } })
        true -> MainNav()
    }
}

@Composable
private fun MainNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onEditAlarm = { id -> nav.navigate("edit/$id") },
                onOpenAbout = { nav.navigate("about") },
                onRunTest = { nav.navigate("test") },
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
        composable("about") {
            AboutScreen(onBack = { nav.popBackStack() }, onOpenLicenses = { nav.navigate("licenses") })
        }
        composable("licenses") { LicensesScreen(onBack = { nav.popBackStack() }) }
        composable("test") {
            TestAlarmScreen(step = null, onDone = { nav.popBackStack() })
        }
    }
}
