package se.lyzio.lyzlarm.ui.challenge

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.lyzio.lyzlarm.app
import se.lyzio.lyzlarm.challenge.ChallengeType
import se.lyzio.lyzlarm.challenge.CodeMemory
import se.lyzio.lyzlarm.challenge.MathProblem
import se.lyzio.lyzlarm.challenge.MemoryPattern
import se.lyzio.lyzlarm.challenge.TypingPhrase
import se.lyzio.lyzlarm.data.ScannedCode
import se.lyzio.lyzlarm.ui.components.PrimaryButton
import se.lyzio.lyzlarm.ui.components.SecondaryButton
import se.lyzio.lyzlarm.ui.theme.Lz
import se.lyzio.lyzlarm.ui.theme.SpaceGrotesk

/** What the challenge unlocks. */
enum class LockedAction(val label: String) { SNOOZE("snooze"), DISMISS("turn off the alarm") }

/**
 * Snooze lock: one challenge, full screen over the lock screen, while the alarm keeps
 * ringing. Wrong answers show a clear Retry with a new challenge of the same type.
 * [onEvent] logs challenge_shown / solved / failed with the type as detail.
 */
@Composable
fun ChallengeScreen(
    action: LockedAction,
    types: Int,
    mathDifficulty: Int,
    onSolved: () -> Unit,
    onBack: () -> Unit,
    onEvent: (type: String, detail: String) -> Unit,
) {
    val context = LocalContext.current
    val cameraAllowed = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    var type by remember { mutableStateOf(ChallengeType.pick(types, cameraAllowed)) }
    // A new key starts a fresh challenge (Retry, or switching away from the barcode).
    var round by remember { mutableIntStateOf(0) }
    LaunchedEffect(type, round) { onEvent(EVENT_SHOWN, type.key) }

    fun failed() = onEvent(EVENT_FAILED, type.key)
    fun solved() {
        onEvent(EVENT_SOLVED, type.key)
        onSolved()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Lz.Ringing)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Snooze lock", color = Lz.RingingTextSoft, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(
            "Solve this to ${action.label}",
            color = Lz.RingingText,
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.SemiBold,
            fontSize = 26.sp,
        )
        androidx.compose.runtime.key(type, round) {
            when (type) {
                ChallengeType.MATH -> MathChallenge(mathDifficulty, ::solved, ::failed) { round++ }
                ChallengeType.MEMORY -> MemoryChallenge(::solved, ::failed) { round++ }
                ChallengeType.TYPING -> TypingChallenge(::solved, ::failed) { round++ }
                ChallengeType.BARCODE -> BarcodeChallenge(::solved, ::failed) {
                    type = ChallengeType.fallback(types)
                    round++
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        SecondaryButton("Back", onBack, border = Lz.RingingButtonBorder, textColor = Lz.RingingText)
        Text(
            "The alarm keeps ringing until you solve it.",
            color = Lz.RingingTextSoft,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

const val EVENT_SHOWN = "challenge_shown"
const val EVENT_SOLVED = "challenge_solved"
const val EVENT_FAILED = "challenge_failed"

@Composable
private fun AnswerField(value: String, onChange: (String) -> Unit, label: String, numbers: Boolean, onDone: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numbers) KeyboardType.Number else KeyboardType.Text,
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Lz.RingingText,
            unfocusedTextColor = Lz.RingingText,
            focusedBorderColor = Lz.AccentSoft,
            unfocusedBorderColor = Lz.RingingButtonBorder,
            focusedLabelColor = Lz.RingingTextSoft,
            unfocusedLabelColor = Lz.RingingTextSoft,
            cursorColor = Lz.AccentSoft,
        ),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
}

@Composable
private fun WrongAnswer(onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Not quite.", color = Lz.AccentSoft, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        PrimaryButton("Retry", onRetry)
    }
}

@Composable
private fun MathChallenge(difficulty: Int, onSolved: () -> Unit, onFailed: () -> Unit, onRetry: () -> Unit) {
    val problem = remember { MathProblem.generate(difficulty) }
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    fun check() {
        if (problem.check(input)) onSolved() else { wrong = true; onFailed() }
    }
    Text(
        "${problem.text} = ?",
        color = Lz.RingingText,
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.SemiBold,
        fontSize = 52.sp,
        modifier = Modifier.semantics { contentDescription = problem.text.replace("×", "times") },
    )
    if (wrong) {
        WrongAnswer(onRetry)
    } else {
        AnswerField(input, { input = it.filter(Char::isDigit).take(6) }, "Answer", numbers = true, onDone = ::check)
        PrimaryButton("Check", ::check, enabled = input.isNotEmpty())
    }
}

@Composable
private fun TypingChallenge(onSolved: () -> Unit, onFailed: () -> Unit, onRetry: () -> Unit) {
    val phrase = remember { TypingPhrase.generate() }
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    fun check() {
        if (phrase.check(input)) onSolved() else { wrong = true; onFailed() }
    }
    Text("Type these words:", color = Lz.RingingTextSoft, fontSize = 15.sp)
    Text(phrase.text, color = Lz.RingingText, fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 30.sp)
    if (wrong) {
        WrongAnswer(onRetry)
    } else {
        AnswerField(input, { input = it.take(60) }, "Words", numbers = false, onDone = ::check)
        PrimaryButton("Check", ::check, enabled = input.isNotBlank())
    }
}

@Composable
private fun MemoryChallenge(onSolved: () -> Unit, onFailed: () -> Unit, onRetry: () -> Unit) {
    val pattern = remember { MemoryPattern.generate() }
    var showing by remember { mutableStateOf(true) }
    var lit by remember { mutableIntStateOf(-1) }
    val input = remember { mutableStateListOf<Int>() }
    var wrong by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(700)
        for (t in pattern.tiles) {
            lit = t
            delay(650)
            lit = -1
            delay(250)
        }
        showing = false
    }
    Text(
        when {
            wrong -> "That was not the pattern."
            showing -> "Watch the pattern (${pattern.tiles.size} tiles)"
            else -> "Now tap it in the same order · ${input.size} of ${pattern.tiles.size}"
        },
        color = Lz.RingingText,
        fontSize = 17.sp,
        fontWeight = FontWeight.Medium,
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in 0 until MemoryPattern.GRID) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (col in 0 until MemoryPattern.GRID) {
                    val tile = row * MemoryPattern.GRID + col
                    val on = lit == tile || (!showing && !wrong && input.lastOrNull() == tile)
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (on) Lz.Accent else Lz.RingingCard)
                            .border(1.dp, Lz.RingingBorder, RoundedCornerShape(16.dp))
                            .semantics { contentDescription = "Tile ${tile + 1}" }
                            .clickable(enabled = !showing && !wrong, role = Role.Button) {
                                input.add(tile)
                                when {
                                    pattern.isSolved(input) -> onSolved()
                                    !pattern.isOnTrack(input) -> { wrong = true; onFailed() }
                                }
                            },
                    )
                }
            }
        }
    }
    if (wrong) WrongAnswer(onRetry)
}

@Composable
private fun BarcodeChallenge(onSolved: () -> Unit, onFailed: () -> Unit, onCantScan: () -> Unit) {
    val context = LocalContext.current
    val dao = context.app.database.extrasDao()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var cameraFailed by remember { mutableStateOf(false) }
    lateinit var analyzer: ZxingAnalyzer
    analyzer = remember {
        ZxingAnalyzer(
            onCode = { format, text ->
                scope.launch {
                    val hash = CodeMemory.fingerprint(format, text)
                    val now = System.currentTimeMillis()
                    val ok = withContext(Dispatchers.IO) {
                        dao.deleteScannedBefore(CodeMemory.cutoff(now))
                        val usable = CodeMemory.usable(dao.scannedAt(hash), now)
                        if (usable) dao.insertScannedCode(ScannedCode(hash, now))
                        usable
                    }
                    if (ok) {
                        onSolved()
                    } else {
                        onFailed()
                        message = "You used this code in the last 7 days. Scan a different one."
                        delay(2_000)
                        analyzer.resume()
                    }
                }
            },
            onFailure = { cameraFailed = true },
        )
    }
    Text("Scan any barcode or QR code you have not used this week, for example on a box in the kitchen.", color = Lz.RingingText, fontSize = 16.sp, lineHeight = 22.sp)
    if (cameraFailed) {
        Text("The camera could not start.", color = Lz.AccentSoft, fontSize = 15.sp)
    } else {
        BarcodeScanner(
            analyzer,
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(Color.Black),
        )
    }
    message?.let { Text(it, color = Lz.AccentSoft, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
    PrimaryButton("Can't scan?", onCantScan)
}
