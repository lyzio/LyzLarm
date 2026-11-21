package se.lyzio.lyzlarm.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.lyzio.lyzlarm.BuildConfig
import se.lyzio.lyzlarm.R
import se.lyzio.lyzlarm.ui.components.LogoBadge
import se.lyzio.lyzlarm.ui.components.LzIcon
import se.lyzio.lyzlarm.ui.setup.startSafely
import se.lyzio.lyzlarm.ui.theme.Lz
import se.lyzio.lyzlarm.ui.theme.SpaceGrotesk

const val SOURCE_URL = "https://github.com/lyzio/LyzLarm"
const val WEBSITE_URL = "https://lyzio.github.io/LyzLarm"

@Composable
fun BackHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) { LzIcon(R.drawable.ic_back, "Back") }
        Text(title, color = Lz.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit, onOpenLicenses: () -> Unit) {
    val context = LocalContext.current
    fun open(url: String) = context.startSafely(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        BackHeader("About", onBack)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LogoBadge()
                Text("LyzLarm", fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, color = Lz.Text)
                Text("Version ${BuildConfig.VERSION_NAME}", color = Lz.TextMuted, fontSize = 14.sp)
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lz.Surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Privacy", color = Lz.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "LyzLarm has no internet permission. It collects nothing, has no accounts and no ads. " +
                        "Your sleep data never leaves this phone unless you export it.",
                    color = Lz.TextSoft,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lz.Surface)) {
                LinkRow("Source code", "github.com/lyzio/LyzLarm", divider = true) { open(SOURCE_URL) }
                LinkRow("Website", "lyzio.github.io/LyzLarm", divider = true) { open(WEBSITE_URL) }
                LinkRow("License", "GPL-3.0-or-later", divider = true) { open("$SOURCE_URL/blob/master/LICENSE") }
                LinkRow("Open-source licenses", "Fonts, libraries", divider = false, onClick = onOpenLicenses)
            }
            Text("Opening a link leaves the app and uses your browser.", color = Lz.TextMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun LinkRow(title: String, value: String, divider: Boolean, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Lz.Text, fontSize = 15.sp)
                Text(value, color = Lz.TextMuted, fontSize = 13.sp)
            }
            LzIcon(R.drawable.ic_chevron, null, tint = Lz.TextMuted, size = 18.dp)
        }
        if (divider) HorizontalDivider(color = Lz.Divider)
    }
}

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val fonts = remember {
        runCatching { context.assets.open("font_licenses.txt").bufferedReader().use { it.readText() } }.getOrDefault("")
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        BackHeader("Open-source licenses", onBack)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Libraries", color = Lz.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Kotlin, Kotlin Coroutines, AndroidX (Core, Activity, Lifecycle, Navigation, Compose, Material 3, Room, DataStore): " +
                    "Apache License 2.0, © The Android Open Source Project, Google LLC and JetBrains s.r.o.",
                color = Lz.TextSoft,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            Text("Fonts: Inter and Space Grotesk, SIL Open Font License 1.1", color = Lz.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(fonts, color = Lz.TextMuted, fontSize = 12.sp, lineHeight = 17.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
