package com.example.executorchllamademo

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.Executors
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

// Below this confidence the app says "not sure" instead of guessing.
const val CONFIDENCE_MIN = 0.35f

// Shows the technical class name and confidence on the result. Set to false for the demo video.
const val SHOW_DEBUG = true

// The 8 trained classes map to the organisers' 4 categories. Only the category is shown to the user.
val CATEGORY_OF = mapOf(
    "organic" to "Biodegradable",
    "glass" to "Recyclable",
    "metal" to "Recyclable",
    "paper_cardboard" to "Recyclable",
    "plastic_bottle_container" to "Recyclable",
    "plastic_film" to "Recyclable",
    "ewaste" to "E-Waste",
    "hazardous" to "Hazardous"
)

fun categoryColor(category: String): Color = when (category) {
    "Biodegradable" -> Color(0xFF3E9B4F)
    "Recyclable" -> Color(0xFF1B7F5C)
    "E-Waste" -> Color(0xFF2E7D4F)
    "Hazardous" -> Color(0xFF14532D)
    else -> Color(0xFF5F7F6C)
}

fun categoryEmoji(category: String): String = when (category) {
    "Biodegradable" -> "🌱"
    "Recyclable" -> "♻️"
    "E-Waste" -> "🔌"
    "Hazardous" -> "☣️"
    else -> "❓"
}

// key = the item type the advice is for (glass, metal...). Empty when only the category is known.
data class ScanItem(
    val bitmap: Bitmap,
    val result: WasteClassifier.Result,
    val info: WasteInfo,
    val key: String = ""
)

class ScanActivity : ComponentActivity() {

    private var classifier: WasteClassifier? = null
    private val lock = Any()
    private val chatEngine = ChatEngine()
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    /** Classifies one cropped item for the live boxes: (category, confidence). */
    private fun classifyCrop(bmp: Bitmap): Pair<String, Float>? {
        return try {
            val c = classifier
            if (c == null) null else {
                val r = synchronized(lock) { c.classify(bmp) }
                val cat = CATEGORY_OF[r.label]
                if (cat == null) null else Pair(cat, r.confidence)
            }
        } catch (e: Exception) {
            null
        }
    }

    private val stillDetector by lazy {
        ObjectDetection.getClient(
            ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .enableMultipleObjects()
                .build()
        )
    }

    /** Finds every item in a photo and classifies each one. Must run off the main thread. */
    private fun classifyAll(bmp: Bitmap): List<ScanItem> {
        return listOf(classify(bmp))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Lang.init(this)

        // Draw behind the status and navigation bars for a modern full-screen look.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false

        // Load the classifier in the background so the first scan is fast.
        Thread {
            try {
                synchronized(lock) {
                    if (classifier == null) classifier = WasteClassifier(this)
                }
            } catch (e: Exception) {
                // classify() will report the error when it is used
            }
        }.start()

        setContent {
            WasteTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) { ScanScreen() }
            }
        }
    }

    private fun classify(bmp: Bitmap): ScanItem {
        val r = synchronized(lock) {
            val c = classifier ?: WasteClassifier(this).also { classifier = it }
            c.classify(bmp)
        }
        val known = r.confidence >= CONFIDENCE_MIN && CATEGORY_OF.containsKey(r.label)
        // TEMPORARY DEBUG LOG
        android.util.Log.d(
            "WasteDebug",
            "Label=${r.label}, Confidence=${r.confidence}, " +
                    "Known=${CATEGORY_OF.containsKey(r.label)}, " +
                    "Accepted=$known"
        )
        val info = KnowledgeBase.get(this, if (known) r.label else "unsure")
        return ScanItem(bmp, r, info, if (known) r.label else "")


    }

    private fun downscale(bmp: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(bmp.width, bmp.height)
        if (longest <= maxSide) return bmp
        val scale = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true
        )
    }

    private fun takePhoto(imageCapture: ImageCapture, onBitmap: (Bitmap) -> Unit, fail: (String) -> Unit) {
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val raw = image.toBitmap()
                        val m = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
                        val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
                        onBitmap(downscale(rotated, 1024))
                    } catch (e: Exception) {
                        fail(e.message ?: "Could not read the photo")
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    fail(exception.message ?: "Camera error")
                }
            }
        )
    }

    @Composable
    private fun CameraPreview(
        modifier: Modifier,
        analyzer: ImageAnalysis.Analyzer?,
        onCamera: (androidx.camera.core.Camera) -> Unit,
        onReady: (ImageCapture) -> Unit
    ) {
        AndroidView(
            modifier = modifier,
            factory = { c ->
                val previewView = PreviewView(c)
                previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
                previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                val providerFuture = ProcessCameraProvider.getInstance(c)
                providerFuture.addListener({
                    // wait until the view has a size, so the camera and the boxes see the same area
                    previewView.post {
                        try {
                            val provider = providerFuture.get()
                            val preview = Preview.Builder().build()
                            preview.setSurfaceProvider(previewView.surfaceProvider)
                            val capture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .build()
                            val group = androidx.camera.core.UseCaseGroup.Builder()
                                .addUseCase(preview)
                                .addUseCase(capture)
                            if (analyzer != null) {
                                val analysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                analysis.setAnalyzer(analysisExecutor, analyzer)
                                group.addUseCase(analysis)
                            }
                            previewView.viewPort?.let { group.setViewPort(it) }
                            provider.unbindAll()
                            val cam = provider.bindToLifecycle(
                                this@ScanActivity, CameraSelector.DEFAULT_BACK_CAMERA, group.build()
                            )
                            onCamera(cam)
                            onReady(capture)
                        } catch (e: Exception) {
                            // the screen shows a message if no camera is ready
                        }
                    }
                }, ContextCompat.getMainExecutor(c))
                previewView
            }
        )
    }

    // ---------------------------------------------------------------- main screen

    @Composable
    private fun ScanScreen() {
        val ctx = LocalContext.current
        val scans = remember { mutableStateListOf<ScanItem>() }
        var selected by remember { mutableStateOf<ScanItem?>(null) }
        var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
        var message by remember { mutableStateOf<String?>(null) }
        var chat by remember { mutableStateOf<ChatRequest?>(null) }
        var tab by remember { mutableStateOf("home") }
        val generalMessages = remember { mutableStateListOf<ChatMessage>() }
        var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
        var torch by remember { mutableStateOf(false) }
        var group by remember { mutableStateOf<List<ScanItem>>(emptyList()) }
        var live by remember { mutableStateOf(DetFrame(1, 1, emptyList())) }
        val analyzer = remember { LiveAnalyzer({ classifyCrop(it) }, { live = it }) }
        var hasCamera by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
            )
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted -> hasCamera = granted }

        // Ask for the camera only when the user opens the camera screen.
        LaunchedEffect(tab) {
            if (tab == "scan" && !hasCamera) permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        fun handleBitmap(bmp: Bitmap) {
            message = tr("Analysing...", "जाँच रहे हैं...")
            Thread {
                try {
                    val found = classifyAll(bmp)
                    runOnUiThread {
                        scans.addAll(found)
                        group = found
                        selected = found[0]
                        message = null
                    }
                } catch (e: Exception) {
                    runOnUiThread { message = "Error: ${e.message}" }
                }
            }.start()
        }

        val picker = rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                try {
                    val source = ImageDecoder.createSource(ctx.contentResolver, uri)
                    val bmp = ImageDecoder.decodeBitmap(source) { decoder, imageInfo, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        if (imageInfo.size.width > 2000) decoder.setTargetSampleSize(4)
                    }
                    handleBitmap(downscale(bmp, 1024))
                } catch (e: Exception) {
                    message = "Error: ${e.message}"
                }
            }
        }

        BackHandler(enabled = tab != "home") { tab = "home" }
        BackHandler(enabled = selected != null) { selected = null }
        BackHandler(enabled = chat != null) { chat = null }

        val openChat = chat
        val current = selected
        if (openChat != null) {
            // Chat about one item or one category: full screen with a back arrow.
            ChatScreen(
                engine = chatEngine,
                request = openChat,
                messages = remember(openChat) { mutableStateListOf<ChatMessage>() },
                showBack = true,
                onBack = { chat = null }
            )
        } else if (current != null) {
            ResultScreen(
                item = current,
                group = group,
                onPick = { selected = it },
                onOther = { text ->
                    generalMessages.clear()
                    chat = ChatRequest(
                        "", text, "",
                        tr(
                            "I have this item: $text. Which category is it: Biodegradable, Recyclable, E-Waste or Hazardous? How should I dispose of it in India?",
                            "मेरे पास यह चीज़ है: $text। यह किस प्रकार का कचरा है: जैव-अपघटनीय, पुनर्चक्रण योग्य, ई-कचरा या खतरनाक? इसे भारत में कैसे फेंकूँ?"
                        )
                    )
                },
                onCorrect = { cat, key ->
                    val fixed = ScanItem(
                        current.bitmap, current.result,
                        KnowledgeBase.get(ctx, if (key.isNotEmpty()) key else cat), key
                    )
                    group = group.map { if (it === current) fixed else it }
                    selected = fixed
                },
                onBack = { selected = null },
                onScanAnother = { selected = null },
                onAsk = {
                    generalMessages.clear()
                    chat = ChatRequest(current.info.toContext(), current.info.title, current.info.category)
                }
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    when (tab) {
                        "home" -> HomeScreen(
                            scans = scans,
                            onScan = { tab = "scan" },
                            onUpload = {
                                picker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onOpenScan = { selected = it },
                            onLearn = {
                                generalMessages.clear()
                                chat = it
                            }
                        )
                        "scan" -> CameraScreen(
                            hasCamera = hasCamera,
                            scans = scans,
                            message = message,
                            analyzer = analyzer,
                            live = live,
                            torchOn = torch,
                            onTorch = {
                                torch = !torch
                                camera?.cameraControl?.enableTorch(torch)
                            },
                            onCamera = { camera = it; torch = false },
                            onReady = { imageCapture = it },
                            onOpenScan = { selected = it },
                            onGallery = {
                                picker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onCapture = {
                                val cap = imageCapture
                                if (cap == null) {
                                    message = "Camera is not ready yet"
                                } else {
                                    takePhoto(cap, { bmp -> handleBitmap(bmp) }, { msg -> message = msg })
                                }
                            }
                        )
                        else -> ChatScreen(
                            engine = chatEngine,
                            request = ChatRequest("", "General questions", ""),
                            messages = generalMessages,
                            showBack = false,
                            onBack = { tab = "home" }
                        )
                    }
                }
                WasteNavBar(tab) { tab = it }
            }
        }
    }

    @Composable
    private fun WasteNavBar(tab: String, onTab: (String) -> Unit) {
        NavigationBar(containerColor = Color(0xFF0E1412)) {
            listOf(
                Triple("home", "home", tr("Home", "होम")),
                Triple("scan", "camera", tr("Scan", "स्कैन")),
                Triple("assistant", "chat", tr("Assistant", "सहायक"))
            ).forEach { (key, emoji, label) ->
                NavigationBarItem(
                    selected = tab == key,
                    onClick = { onTab(key) },
                    icon = { Icon24(emoji, if (tab == key) HomeMint else HomeMuted, 24.dp) },
                    label = { Text(label) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color(0xFF1F3A2F),
                        selectedTextColor = HomeMint,
                        unselectedTextColor = HomeMuted
                    )
                )
            }
        }
    }

    // ---------------------------------------------------------------- camera screen

    @Composable
    private fun CameraScreen(
        hasCamera: Boolean,
        scans: List<ScanItem>,
        message: String?,
        analyzer: ImageAnalysis.Analyzer,
        live: DetFrame,
        torchOn: Boolean,
        onTorch: () -> Unit,
        onCamera: (androidx.camera.core.Camera) -> Unit,
        onReady: (ImageCapture) -> Unit,
        onOpenScan: (ScanItem) -> Unit,
        onGallery: () -> Unit,
        onCapture: () -> Unit
    ) {
        val haptic = LocalHapticFeedback.current

        Box(modifier = Modifier
            .fillMaxSize()
            .background(HomeBg)) {

            if (hasCamera) {
                CameraPreview(Modifier.fillMaxSize(), analyzer, onCamera, onReady)
            } else {
                Text(
                    tr("Camera permission is needed to scan.\nYou can still choose a photo from the gallery.", "स्कैन करने के लिए कैमरे की अनुमति चाहिए।\nआप गैलरी से फ़ोटो चुन सकते हैं।"),
                    color = HomeText,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp)
                )
            }

            // Boxes around the items the app recognises
            if (hasCamera) DetectionOverlay(live)

            // Top: title and a live status
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xCC0B100E), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(tr("Scan", "स्कैन"), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    when (live.boxes.size) {
                        0 -> tr("Looking for items...", "वस्तुएँ खोज रहे हैं...")
                        1 -> tr("1 item found", "1 वस्तु मिली")
                        else -> tr("${live.boxes.size} items found", "${live.boxes.size} वस्तुएँ मिलीं")
                    },
                    color = if (live.boxes.isEmpty()) Color(0xB3FFFFFF) else HomeMint,
                    fontSize = 13.sp
                )
            }

            // Bottom controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE0B100E))))
                    .padding(top = 36.dp, bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                message?.let {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xCC141B18))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) { Text(it, color = Color(0xFFFFB4A9), fontSize = 13.sp) }
                }

                // Last scan, tap to reopen it
                scans.lastOrNull()?.let { last ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xE6141B18))
                            .clickable { onOpenScan(last) }
                            .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            bitmap = last.bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(tr("Last scan", "पिछला स्कैन"), color = HomeMuted, fontSize = 13.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            catLabel(last.info.category),
                            color = boxColor(last.info.category),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Colour key
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    for (cat in listOf("Biodegradable", "Recyclable", "E-Waste", "Hazardous")) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(boxColor(cat)))
                            Spacer(Modifier.width(5.dp))
                            Text(catLabel(cat), color = Color(0xCCFFFFFF), fontSize = 11.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 36.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SideButton("gallery", tr("Gallery", "गैलरी"), false, onGallery)

                    // Shutter: white ring with a filled centre
                    Box(
                        modifier = Modifier
                            .size(78.dp)
                            .border(4.dp, Color.White, CircleShape)
                            .padding(7.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCapture()
                            }
                    )

                    SideButton("flash", if (torchOn) tr("Light on", "लाइट चालू") else tr("Light", "लाइट"), torchOn, onTorch)
                }
            }
        }
    }

    @Composable
    private fun SideButton(icon: String, label: String, active: Boolean, onClick: () -> Unit) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (active) HomeMint else Color(0xE61B2521))
                    .clickable { onClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon24(icon, if (active) Color(0xFF0B2A1D) else Color.White, 24.dp)
            }
            Spacer(Modifier.height(5.dp))
            Text(label, color = Color(0xCCFFFFFF), fontSize = 11.sp)
        }
    }

    @Composable
    private fun RoundAction(icon: String, label: String, onClick: () -> Unit) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEAF1EB))
                    .clickable { onClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon24(icon, Color(0xFF1B6B43), 24.dp)
            }
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color(0xFF55605A))
        }
    }

    @Composable
    private fun Chip(text: String, color: Color) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.14f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(text, color = color, style = MaterialTheme.typography.labelMedium)
        }
    }

    // ---------------------------------------------------------------- result screen

    private fun binOf(category: String): String = when (category) {
        "Biodegradable" -> tr("Green bin", "हरा डिब्बा")
        "Recyclable" -> tr("Blue bin", "नीला डिब्बा")
        "E-Waste" -> tr("E-waste drop-off point", "ई-कचरा संग्रह केंद्र")
        "Hazardous" -> tr("Hazardous waste collection", "खतरनाक कचरा संग्रह")
        else -> tr("Not sure yet", "अभी पक्का नहीं")
    }

    @Composable
    private fun DarkCard(content: @Composable ColumnScope.() -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(HomeSurface)
                .border(1.dp, HomeOutline, RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }

    @Composable
    private fun SectionHeader(icon: String, title: String, tint: Color, hint: String? = null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon24(icon, tint, 20.dp)
            Spacer(Modifier.width(8.dp))
            Text(title, color = HomeText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (hint != null) {
                Spacer(Modifier.weight(1f))
                Text(hint, color = HomeMuted, fontSize = 12.sp)
            }
        }
    }

    @Composable
    private fun ResultScreen(
        item: ScanItem,
        group: List<ScanItem>,
        onPick: (ScanItem) -> Unit,
        onCorrect: (String, String) -> Unit,
        onOther: (String) -> Unit,
        onBack: () -> Unit,
        onScanAnother: () -> Unit,
        onAsk: () -> Unit
    ) {
        val info = KnowledgeBase.get(LocalContext.current, item.key.ifEmpty { item.info.category })
        val accent = boxColor(info.category)
        val confident = item.result.confidence >= CONFIDENCE_MIN
        val pct = (item.result.confidence * 100).toInt()
        val done = remember(info) {
            mutableStateListOf<Boolean>().apply { repeat(info.steps.size) { add(false) } }
        }

        var otherOpen by remember { mutableStateOf(false) }
        var otherText by remember { mutableStateOf("") }

        Box(modifier = Modifier
            .fillMaxSize()
            .background(HomeBg)
            .imePadding()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp)
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Back and title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(HomeSurface2)
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) { Icon24("back", HomeText, 20.dp) }
                    Spacer(Modifier.width(14.dp))
                    Text(tr("Result", "परिणाम"), color = HomeText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                // Several items in one photo
                if (group.size > 1) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(group) { i, g ->
                            val sel = g === item
                            val c = boxColor(g.info.category)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (sel) c else HomeSurface2)
                                    .clickable { onPick(g) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    "${tr("Item", "वस्तु")} ${i + 1}: ${catLabel(g.info.category)}",
                                    color = if (sel) Color(0xFF0B2A1D) else HomeText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Photo, category and bin
                DarkCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(100.dp)) {
                            Image(
                                bitmap = item.bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(18.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xCC0B100E))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon24("check", accent, 12.dp)
                                Spacer(Modifier.width(3.dp))
                                Text("$pct%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(catLabel(info.category), color = HomeText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            if (item.key.isNotEmpty()) {
                                Text(info.title, color = HomeMuted, fontSize = 14.sp)
                            }
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(accent.copy(alpha = 0.18f))
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon24(categoryIcon(info.category), accent, 16.dp)
                                Spacer(Modifier.width(7.dp))
                                Text(binOf(info.category), color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            if (!confident) {
                                Text(tr("Not sure. Pick what it is below.", "पक्का नहीं। नीचे चुनें कि यह क्या है।"), color = HomeMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Correct the guess. People know what an item is made of, not which category it is,
                // so the options are everyday materials and the category follows from the choice.
                DarkCard {
                    Text(
                        if (confident) tr("Wrong? Tell us what it is", "गलत है? बताइए यह क्या है")
                        else tr("What is it made of?", "यह किससे बना है?"),
                        color = HomeText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                    )
                    val options = listOf(
                        Triple(tr("Food or peels", "खाना या छिलके"), "Biodegradable", "organic"),
                        Triple(tr("Paper or cardboard", "कागज़ या गत्ता"), "Recyclable", "paper_cardboard"),
                        Triple(tr("Glass", "काँच"), "Recyclable", "glass"),
                        Triple(tr("Metal", "धातु"), "Recyclable", "metal"),
                        Triple(tr("Plastic bottle", "प्लास्टिक की बोतल"), "Recyclable", "plastic_bottle_container"),
                        Triple(tr("Plastic wrapper", "प्लास्टिक का रैपर"), "Recyclable", "plastic_film"),
                        Triple(tr("Electronics or battery", "इलेक्ट्रॉनिक या बैटरी"), "E-Waste", "ewaste"),
                        Triple(tr("Medicine, bulb, chemical", "दवा, बल्ब, रसायन"), "Hazardous", "hazardous")
                    )
                    for (row in options.chunked(2)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for ((label, cat, key) in row) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(HomeSurface2)
                                        .clickable { onCorrect(cat, key) }
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(boxColor(cat)))
                                    Spacer(Modifier.width(8.dp))
                                    Text(label, color = HomeText, fontSize = 13.sp, lineHeight = 16.sp)
                                }
                            }
                        }
                    }

                    // Something else: type it and ask the assistant, or choose the category yourself
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (otherOpen) HomeMint.copy(alpha = 0.18f) else HomeSurface2)
                            .clickable { otherOpen = !otherOpen }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon24("info", HomeMint, 18.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            tr("Something else", "कुछ और"),
                            color = HomeText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (otherOpen) {
                        BasicTextField(
                            value = otherText,
                            onValueChange = { otherText = it },
                            singleLine = true,
                            textStyle = TextStyle(color = HomeText, fontSize = 15.sp),
                            cursorBrush = SolidColor(HomeMint),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(HomeSurface2)
                                        .border(1.dp, HomeOutline, RoundedCornerShape(14.dp))
                                        .padding(14.dp)
                                ) {
                                    if (otherText.isEmpty()) {
                                        Text(
                                            tr("Type the item, for example: old toothbrush", "चीज़ का नाम लिखें, जैसे: पुराना टूथब्रश"),
                                            color = HomeMuted, fontSize = 14.sp
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (otherText.isBlank()) HomeSurface2 else HomeMint)
                                .clickable { if (otherText.isNotBlank()) onOther(otherText.trim()) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                tr("Ask the assistant", "सहायक से पूछें"),
                                color = if (otherText.isBlank()) HomeMuted else Color(0xFF0B2A1D),
                                fontSize = 14.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            tr("Or choose the category yourself", "या खुद प्रकार चुनें"),
                            color = HomeMuted, fontSize = 12.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (cat in listOf("Biodegradable", "Recyclable", "E-Waste", "Hazardous")) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(boxColor(cat).copy(alpha = 0.2f))
                                        .clickable { onCorrect(cat, "") }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        catLabel(cat), color = boxColor(cat), fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold, maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                if (SHOW_DEBUG) {
                    Text(
                        "Debug: ${item.result.label} (${pct}%) in ${item.result.millis} ms",
                        color = HomeMuted, fontSize = 11.sp
                    )
                }

                // Steps you can tick off
                if (info.steps.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader("check", tr("What to do", "क्या करें"), HomeMint, tr("Tap a step to tick it", "स्टेप पर टैप करके टिक करें"))
                        info.steps.forEachIndexed { i, step ->
                            val isDone = done.getOrElse(i) { false }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(HomeSurface)
                                    .border(1.dp, HomeOutline, RoundedCornerShape(18.dp))
                                    .clickable { if (i < done.size) done[i] = !done[i] }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(if (isDone) HomeMint else HomeSurface2),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isDone) Icon24("check", Color(0xFF0B2A1D), 18.dp)
                                    else Text("${i + 1}", color = HomeMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    step,
                                    color = if (isDone) HomeMuted else HomeText,
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }
                }

                if (info.dont.isNotEmpty()) {
                    DarkCard {
                        SectionHeader("cross", tr("Avoid", "क्या न करें"), Color(0xFFE57373))
                        info.dont.forEach {
                            Text("•  $it", color = HomeMuted, fontSize = 14.sp, lineHeight = 21.sp)
                        }
                    }
                }

                if (info.why.isNotEmpty()) {
                    DarkCard {
                        SectionHeader("globe", tr("Why it matters", "यह क्यों ज़रूरी है"), HomeMint)
                        Text(info.why, color = HomeMuted, fontSize = 14.sp, lineHeight = 21.sp)
                    }
                }

                if (info.tip.isNotEmpty()) {
                    DarkCard {
                        SectionHeader("bulb", tr("Tip", "सुझाव"), HomeMint)
                        Text(info.tip, color = HomeMuted, fontSize = 14.sp, lineHeight = 21.sp)
                    }
                }

                if (info.items.isNotEmpty() || info.journey.isNotEmpty()) {
                    DarkCard {
                        SectionHeader(
                            "recycle",
                            if (item.key.isNotEmpty()) tr("About ${info.title}", "${info.title} के बारे में")
                            else tr("Common items", "आम चीज़ें"),
                            HomeMint
                        )
                        info.items.forEachIndexed { i, (name, tip) ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(HomeOutline))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(name, color = HomeText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(tip, color = HomeMuted, fontSize = 13.sp, lineHeight = 19.sp)
                            }
                        }
                        if (info.journey.isNotEmpty()) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(HomeOutline))
                            Text(
                                tr("What happens next", "इसके बाद क्या होता है"),
                                color = HomeMint, fontSize = 13.sp, fontWeight = FontWeight.Bold
                            )
                            Text(info.journey, color = HomeMuted, fontSize = 13.sp, lineHeight = 19.sp)
                        }
                    }
                }

                info.warning?.let {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0x22FFB300))
                            .border(1.dp, Color(0x55FFB300), RoundedCornerShape(20.dp))
                            .padding(16.dp)
                    ) {
                        Icon24("hazard", Color(0xFFFFB300), 22.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(it, color = Color(0xFFFFE0A3), fontSize = 14.sp, lineHeight = 21.sp)
                    }
                }

                // Space so the last card is not hidden behind the bottom bar
                Spacer(Modifier.height(100.dp))
            }

            // Bottom action bar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, HomeBg, HomeBg)))
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp)
                    .padding(top = 26.dp, bottom = 14.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(HomeSurface)
                            .border(1.dp, HomeOutline, RoundedCornerShape(16.dp))
                            .clickable { onScanAnother() },
                        contentAlignment = Alignment.Center
                    ) { Text(tr("Scan another", "दोबारा स्कैन करें"), color = HomeText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                    Box(
                        modifier = Modifier
                            .weight(1.4f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(HomeMint)
                            .clickable { onAsk() },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon24("chat", Color(0xFF0B2A1D), 20.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(tr("Ask about this", "इसके बारे में पूछें"), color = Color(0xFF0B2A1D), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}