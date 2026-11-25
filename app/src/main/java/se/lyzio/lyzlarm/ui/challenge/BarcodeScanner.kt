package se.lyzio.lyzlarm.ui.challenge

import android.os.Handler
import android.os.Looper
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

/**
 * Camera preview that reads any barcode or QR code with ZXing (open source, offline).
 * [analyzer] calls back once per code; call [ZxingAnalyzer.resume] to look for another.
 */
@Composable
fun BarcodeScanner(analyzer: ZxingAnalyzer, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val providerFuture = remember { ProcessCameraProvider.getInstance(context) }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { providerFuture.get().unbindAll() }
            executor.shutdown()
        }
    }
    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            providerFuture.addListener({
                runCatching {
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(executor, analyzer) }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }.onFailure { analyzer.onError(it) }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = modifier,
    )
}

/** Decodes camera frames with ZXing; every other frame is rotated so codes are found either way up. */
class ZxingAnalyzer(
    private val onCode: (format: String, text: String) -> Unit,
    private val onFailure: (Throwable) -> Unit = {},
) : ImageAnalysis.Analyzer {
    private val main = Handler(Looper.getMainLooper())
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.POSSIBLE_FORMATS to BarcodeFormat.values().filter { it != BarcodeFormat.UPC_EAN_EXTENSION },
            ),
        )
    }
    private var frame = 0

    @Volatile private var paused = false

    fun resume() {
        paused = false
    }

    fun onError(t: Throwable) {
        main.post { onFailure(t) }
    }

    override fun analyze(image: ImageProxy) {
        try {
            if (paused) return
            val plane = image.planes[0]
            val w = image.width
            val h = image.height
            val buffer = plane.buffer
            val stride = plane.rowStride
            val y = ByteArray(w * h)
            for (row in 0 until h) {
                buffer.position(row * stride)
                buffer.get(y, row * w, w)
            }
            val rotate = frame++ % 2 == 1
            val bytes = if (rotate) rotate90(y, w, h) else y
            val bw = if (rotate) h else w
            val bh = if (rotate) w else h
            val source = PlanarYUVLuminanceSource(bytes, bw, bh, 0, 0, bw, bh, false)
            val result = try {
                reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
            } catch (e: Exception) {
                null
            } finally {
                reader.reset()
            }
            if (result != null && result.text.isNotEmpty()) {
                paused = true
                val format = result.barcodeFormat.name
                val text = result.text
                main.post { onCode(format, text) }
            }
        } catch (e: Exception) {
            // A bad frame: try the next one.
        } finally {
            image.close()
        }
    }

    private fun rotate90(src: ByteArray, w: Int, h: Int): ByteArray {
        val out = ByteArray(src.size)
        var i = 0
        for (x in 0 until w) {
            for (yy in h - 1 downTo 0) out[i++] = src[yy * w + x]
        }
        return out
    }
}
