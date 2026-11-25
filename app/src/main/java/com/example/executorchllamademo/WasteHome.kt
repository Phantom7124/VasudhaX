package com.example.executorchllamademo

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Dark palette used by the home screen and the bottom bar
val HomeBg = Color(0xFF0B100E)
val HomeSurface = Color(0xFF141B18)
val HomeSurface2 = Color(0xFF1B2521)
val HomeOutline = Color(0xFF2A3832)
val HomeText = Color(0xFFE8F1EC)
val HomeMuted = Color(0xFF8FA39A)
val HomeMint = Color(0xFF7ED3A9)
private val OnMint = Color(0xFF0B2A1D)

/** App name. Change it here (and in strings.xml for the name under the app icon). */
const val APP_NAME = "Saaf"

private data class Stream(val category: String, val hint: String, val icon: String)

// The four organiser categories (the 8 trained classes all map into these)
private fun streams() = listOf(
    Stream("Biodegradable", tr("Green bin: food scraps, peels, leaves", "हरा डिब्बा: खाने का कचरा, छिलके, पत्ते"), "leaf"),
    Stream("Recyclable", tr("Blue bin: paper, glass, metal, plastic", "नीला डिब्बा: कागज़, काँच, धातु, प्लास्टिक"), "recycle"),
    Stream("E-Waste", tr("Drop-off point: phones, chargers, batteries", "ई-कचरा केंद्र: फ़ोन, चार्जर, बैटरी"), "chip"),
    Stream("Hazardous", tr("Special collection: medicines, bulbs, sprays", "विशेष संग्रह: दवाइयाँ, बल्ब, स्प्रे"), "hazard")
)

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(HomeOutline))
}

private fun greeting(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> tr("Good morning", "सुप्रभात")
        hour < 17 -> tr("Good afternoon", "नमस्कार")
        else -> tr("Good evening", "शुभ संध्या")
    }
}

/** Home: header, camera card, a bin guide, and the user's own recent scans. */
@Composable
fun HomeScreen(
    scans: List<ScanItem>,
    onScan: () -> Unit,
    onUpload: () -> Unit,
    onOpenScan: (ScanItem) -> Unit,
    onLearn: (ChatRequest) -> Unit
) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeBg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // ---------------------------------------------------------- header
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon24("recycle", HomeMint, 26.dp)
                Spacer(Modifier.width(10.dp))
                Text(APP_NAME, color = HomeText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                // Language switch
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(HomeSurface2)
                        .padding(3.dp)
                ) {
                    for ((label, isHindi) in listOf("EN" to false, "हिं" to true)) {
                        val on = Lang.hindi == isHindi
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (on) HomeMint else Color.Transparent)
                                .clickable { Lang.set(ctx, isHindi) }
                                .padding(horizontal = 13.dp, vertical = 6.dp)
                        ) {
                            Text(
                                label,
                                color = if (on) OnMint else HomeMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("${greeting()}.", color = HomeText, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(
                tr("Point the camera at what you are about to throw away.", "जिसे फेंकने जा रहे हैं, उस पर कैमरा घुमाइए।"),
                color = HomeMuted, fontSize = 15.sp, lineHeight = 22.sp
            )
        }

        // ---------------------------------------------------------- camera card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(HomeSurface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1A2420)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)) {
                    val len = 20.dp.toPx()
                    val sw = 3.dp.toPx()
                    val col = HomeMint.copy(alpha = 0.8f)
                    fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                        drawLine(col, Offset(x, y), Offset(x + dx * len, y), sw, StrokeCap.Round)
                        drawLine(col, Offset(x, y), Offset(x, y + dy * len), sw, StrokeCap.Round)
                    }
                    corner(0f, 0f, 1f, 1f)
                    corner(size.width, 0f, -1f, 1f)
                    corner(0f, size.height, 1f, -1f)
                    corner(size.width, size.height, -1f, -1f)
                }
                Text(tr("Frame the item you want to check", "जाँचने वाली चीज़ को फ़्रेम में रखें"), color = HomeMuted, fontSize = 13.sp)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(HomeMint)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onScan()
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon24("camera", OnMint, 22.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(tr("Scan an item", "स्कैन करें"), color = OnMint, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, HomeOutline, RoundedCornerShape(16.dp))
                    .clickable { onUpload() },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon24("gallery", HomeText, 20.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Choose from gallery", "गैलरी से चुनें"), color = HomeText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // ---------------------------------------------------------- bin guide
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("Which bin does it go in?", "यह किस डिब्बे में जाएगा?"), color = HomeText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(HomeSurface)
                    .padding(horizontal = 16.dp)
            ) {
                val list = streams()
                list.forEachIndexed { i, st ->
                    val accent = boxColor(st.category)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val info = KnowledgeBase.get(ctx, st.category)
                                onLearn(ChatRequest(info.toContext(), info.title, info.category))
                            }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) { Icon24(st.icon, accent, 22.dp) }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(catLabel(st.category), color = HomeText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(st.hint, color = HomeMuted, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                    }
                    if (i < list.size - 1) Divider()
                }
            }
        }

        // ---------------------------------------------------------- recent scans (real data)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("Your recent scans", "आपके हाल के स्कैन"), color = HomeText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (scans.isEmpty()) {
                Text(
                    tr("Nothing scanned yet. Your scans will show up here.", "अभी कुछ स्कैन नहीं हुआ। आपके स्कैन यहाँ दिखेंगे।"),
                    color = HomeMuted, fontSize = 14.sp, lineHeight = 20.sp
                )
            } else {
                val recent = scans.takeLast(3).reversed()
                recent.forEachIndexed { i, item ->
                    val accent = boxColor(item.info.category)
                    val pct = (item.result.confidence * 100).toInt()
                    val li = KnowledgeBase.get(ctx, item.info.category)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenScan(item) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            bitmap = item.bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(li.title, color = HomeText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                li.steps.firstOrNull() ?: "",
                                color = HomeMuted, fontSize = 12.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("$pct%", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    if (i < recent.size - 1) Divider()
                }
            }
        }
    }
}