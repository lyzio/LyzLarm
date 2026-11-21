package se.lyzio.lyzlarm.ui.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import se.lyzio.lyzlarm.ui.theme.Lz
import se.lyzio.lyzlarm.ui.theme.SpaceGrotesk

/** The three main tabs: Alarms, Sleep (arrives in v0.2), Settings. */
@Composable
fun HomeScreen(onEditAlarm: (Long) -> Unit, onOpenAbout: () -> Unit, onRunTest: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.weight(1f)) {
            when (tab) {
                0 -> AlarmsScreen(onEditAlarm)
                1 -> SleepPlaceholder()
                else -> SettingsScreen(onOpenAbout = onOpenAbout, onRunTest = onRunTest)
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

@Composable
private fun SleepPlaceholder() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Sleep", fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, color = Lz.Text)
        Column(
            Modifier
                .fillMaxWidth()
                .background(Lz.Surface, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                .border(1.dp, Lz.Outline, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LzIcon(R.drawable.ic_moon, null, tint = Lz.AccentSoft, size = 28.dp)
            Text("Sleep tracking arrives in v0.2", color = Lz.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Put the phone on the mattress and LyzLarm will show how you slept every morning. " +
                    "Until then, your alarms ring at the exact time you set.",
                color = Lz.TextMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
    }
}
