package se.lyzio.lyzlarm.ui.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.lyzio.lyzlarm.R
import se.lyzio.lyzlarm.ui.alarms.AlarmsScreen
import se.lyzio.lyzlarm.ui.components.LzIcon
import se.lyzio.lyzlarm.ui.settings.SettingsScreen
import se.lyzio.lyzlarm.ui.sleep.NightsScreen
import se.lyzio.lyzlarm.ui.theme.Lz

/** The three main tabs: Alarms, Sleep (past nights), Settings. */
@Composable
fun HomeScreen(
    onEditAlarm: (Long) -> Unit,
    onOpenAbout: () -> Unit,
    onRunTest: () -> Unit,
    onStartSleep: () -> Unit,
    onOpenBedtime: () -> Unit,
    onOpenNight: (Long) -> Unit,
    onOpenTracking: () -> Unit,
    onOpenAlarmDefaults: () -> Unit,
    onOpenEmptyBed: () -> Unit,
    initialTab: Int = 0,
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.weight(1f)) {
            when (tab) {
                0 -> AlarmsScreen(onEditAlarm, onStartSleep, onOpenBedtime, onOpenNight)
                1 -> NightsScreen(onOpenNight)
                else -> SettingsScreen(
                    onOpenAbout = onOpenAbout,
                    onRunTest = onRunTest,
                    onOpenTracking = onOpenTracking,
                    onOpenAlarmDefaults = onOpenAlarmDefaults,
                    onOpenEmptyBed = onOpenEmptyBed,
                )
            }
        }
        HorizontalDivider(color = Lz.Divider)
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            NavItem("Alarms", R.drawable.ic_alarm, tab == 0) { tab = 0 }
            NavItem("Sleep", R.drawable.ic_sleep, tab == 1) { tab = 1 }
            NavItem("Settings", R.drawable.ic_settings, tab == 2) { tab = 2 }
        }
    }
}

@Composable
private fun NavItem(label: String, @DrawableRes icon: Int, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) Lz.AccentText else Lz.TextMuted
    Column(
        Modifier
            .widthIn(min = 88.dp)
            .heightIn(min = 48.dp)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LzIcon(icon, null, tint = color)
        Text(label, color = color, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
    }
}
