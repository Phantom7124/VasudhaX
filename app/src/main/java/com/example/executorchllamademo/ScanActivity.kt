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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
const val SHOW_DEBUG = false

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

data class ScanItem(val bitmap: Bitmap, val result: WasteClassifier.Result, val info: WasteInfo)

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
        val items = ArrayList<ScanItem>()
        try {
            val objs = Tasks.await(stillDetector.process(InputImage.fromBitmap(bmp, 0)))
            for (o in objs) {
                val r = o.boundingBox
                val pad = 0.08f * maxOf(r.width(), r.height())
                val l = maxOf(0f, r.left - pad).toInt()
                val t = maxOf(0f, r.top - pad).toInt()
                val rr = minOf(bmp.width.toFloat(), r.right + pad).toInt()
                val bb = minOf(bmp.height.toFloat(), r.bottom + pad).toInt()
                if (rr - l < 48 || bb - t < 48) continue
                items.add(classify(Bitmap.createBitmap(bmp, l, t, rr - l, bb - t)))
            }
        } catch (e: Exception) {
            // fall back to the whole photo below
        }
        if (items.isEmpty()) items.add(classify(bmp))
        return items
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw behind the status and navigation bars for a modern full-screen look.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = true

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
        val key = if (r.confidence >= CONFIDENCE_MIN) (CATEGORY_OF[r.label] ?: "unsure") else "unsure"
        return ScanItem(bmp, r, KnowledgeBase.get(this, key))
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
    private fun CameraPreview(modifier: Modifier, analyzer: ImageAnalysis.Analyzer?, onReady: (ImageCapture) -> Unit) {
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
                            provider.bindToLifecycle(
                                this@ScanActivity, CameraSelector.DEFAULT_BACK_CAMERA, group.build()
                            )
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
            message = "Analysing..."
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
                onCorrect = { cat ->
                    val fixed = ScanItem(current.bitmap, current.result, KnowledgeBase.get(ctx, cat))
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
                            onScan = { tab = "scan" },
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
        NavigationBar(containerColor = Color.White) {
            listOf(
                Triple("home", "home", "Home"),
                Triple("scan", "camera", "Scan"),
                Triple("assistant", "chat", "Assistant")
            ).forEach { (key, emoji, label) ->
                NavigationBarItem(
                    selected = tab == key,
                    onClick = { onTab(key) },
                    icon = { Icon24(emoji, if (tab == key) Color(0xFF14532D) else Color(0xFF6B7F73), 24.dp) },
                    label = { Text(label) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color(0xFFD3EADB),
                        selectedTextColor = Color(0xFF14532D)
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
        onReady: (ImageCapture) -> Unit,
        onOpenScan: (ScanItem) -> Unit,
        onGallery: () -> Unit,
        onCapture: () -> Unit
    ) {
        val transition = rememberInfiniteTransition(label = "scan")
        val sweep by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "sweep"
        )

        Box(modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)) {

            if (hasCamera) {
                CameraPreview(Modifier.fillMaxSize(), analyzer, onReady)
            } else {
                Text(
                    "Camera permission is needed to scan.\nYou can still pick a photo from the gallery.",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp)
                )
            }

            // Coloured squares around every item the app recognises
            if (hasCamera) DetectionOverlay(live)

            // Legend
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 78.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (cat in listOf("Biodegradable", "Recyclable", "E-Waste", "Hazardous")) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0x99000000))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(boxColor(cat)))
                        Spacer(Modifier.width(5.dp))
                        Text(cat, color = Color.White, fontSize = 10.sp)
                    }
                }
            }

            // Top bar
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    "Scan waste",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Point at items. Boxes appear on their own",
                    color = Color(0xCCFFFFFF),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Bottom sheet
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = Color.White,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    message?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }

                    if (scans.isNotEmpty()) {
                        Text(
                            "This session: ${scans.size} item" + if (scans.size == 1) "" else "s",
                            style = MaterialTheme.typography.titleSmall
                        )
                        val counts = scans.groupingBy { it.info.category }.eachCount().toList()
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(counts) { _, pair ->
                                Chip(
                                    text = pair.first + "  " + pair.second,
                                    color = categoryColor(pair.first)
                                )
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(scans) { _, s ->
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(categoryColor(s.info.category))
                                        .padding(2.5.dp)
                                        .clickable { onOpenScan(s) }
                                ) {
                                    Image(
                                        bitmap = s.bitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(10.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RoundAction(icon = "gallery", label = "Gallery", onClick = onGallery)

                        // Capture button: ring with a filled centre
                        Box(
                            modifier = Modifier
                                .size(78.dp)
                                .border(4.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                .padding(7.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable { onCapture() }
                        )

                        Spacer(Modifier.size(52.dp))
                    }
                }
            }
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

    @Composable
    private fun ResultScreen(
        item: ScanItem,
        group: List<ScanItem>,
        onPick: (ScanItem) -> Unit,
        onCorrect: (String) -> Unit,
        onBack: () -> Unit,
        onScanAnother: () -> Unit,
        onAsk: () -> Unit
    ) {
        val info = item.info
        val accent = categoryColor(info.category)
        val confident = item.result.confidence >= CONFIDENCE_MIN

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Photo with a dark fade and a back button
                Box {
                    Image(
                        bitmap = item.bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        contentScale = ContentScale.Crop
                    )
                    if (group.size > 1) {
                        LazyRow(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(group) { i, g ->
                                val sel = g === item
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(if (sel) categoryColor(g.info.category) else Color(0x99000000))
                                        .clickable { onPick(g) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        "Item ${i + 1}: ${g.info.category}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent)))
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(16.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0x66000000))
                            .clickable { onBack() }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon24("back", Color.White, 18.dp)
                            Spacer(Modifier.width(4.dp))
                            Text("Back", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                // Content, overlapping the photo with rounded top corners
                Column(
                    modifier = Modifier
                        .offset(y = (-28).dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Category header
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(accent.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon24(categoryIcon(info.category), accent, 34.dp)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "CATEGORY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF7A857E)
                                )
                                Text(
                                    info.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = accent
                                )
                                Chip(
                                    text = if (confident) "Confident match" else "Not sure",
                                    color = if (confident) Color(0xFF1B6B43) else Color(0xFF6B7F73)
                                )
                                MatchBar(item.result.confidence, accent)
                            }
                        }
                    }

                    // Let the user correct the guess; the advice below changes to match
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                if (confident) "Not right? Pick the correct category"
                                else "Not sure about this one. What is it?",
                                style = MaterialTheme.typography.titleSmall
                            )
                            for (row in listOf(listOf("Biodegradable", "Recyclable"), listOf("E-Waste", "Hazardous"))) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    for (cat in row) {
                                        val on = info.category == cat
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(if (on) categoryColor(cat) else categoryColor(cat).copy(alpha = 0.12f))
                                                .clickable { onCorrect(cat) }
                                                .padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon24(categoryIcon(cat), if (on) Color.White else categoryColor(cat), 18.dp)
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    cat,
                                                    color = if (on) Color.White else categoryColor(cat),
                                                    style = MaterialTheme.typography.labelLarge
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (SHOW_DEBUG) {
                        Text(
                            "Debug: ${item.result.label} (${(item.result.confidence * 100).toInt()}%) in ${item.result.millis} ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF7A857E)
                        )
                    }

                    InfoCard("check", "What to do", info.steps, numbered = true)
                    InfoCard("cross", "Avoid", info.dont, numbered = false)
                    if (info.why.isNotEmpty()) InfoCard("globe", "Why it matters", listOf(info.why), numbered = false)
                    if (info.tip.isNotEmpty()) InfoCard("bulb", "Tip", listOf(info.tip), numbered = false)

                    info.warning?.let {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFDDEFE3))
                        ) {
                            Row(modifier = Modifier.padding(16.dp)) {
                                Icon24("hazard", Color(0xFF14532D), 22.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(it, color = Color(0xFF14532D), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                // Space so the last card is not hidden behind the bottom bar
                Spacer(Modifier.height(96.dp))
            }

            // Bottom action bar
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = Color.White,
                shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onScanAnother,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("Scan another") }
                    Button(
                        onClick = onAsk,
                        modifier = Modifier
                            .weight(1.4f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("Ask about this item") }
                }
            }
        }
    }

    @Composable
    private fun MatchBar(value: Float, color: Color) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Match strength",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF7A857E)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color.copy(alpha = 0.15f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(value.coerceIn(0.03f, 1f))
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(color)
                )
            }
        }
    }

    @Composable
    private fun InfoCard(icon: String, title: String, lines: List<String>, numbered: Boolean) {
        if (lines.isEmpty()) return
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon24(icon, Color(0xFF1B6B43), 20.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
                lines.forEachIndexed { index, line ->
                    val prefix = when {
                        lines.size == 1 -> ""
                        numbered -> "${index + 1}.  "
                        else -> "•  "
                    }
                    Text(prefix + line, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF2B332E))
                }
            }
        }
    }
}