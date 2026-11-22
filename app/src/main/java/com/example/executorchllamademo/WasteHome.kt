package com.example.executorchllamademo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BIN_EXAMPLES = listOf(
    "Biodegradable" to "Food scraps, peels, leaves",
    "Recyclable" to "Paper, glass, metal, plastic bottles",
    "E-Waste" to "Phones, chargers, batteries",
    "Hazardous" to "Medicines, chemicals, bulbs"
)

private val FACTS = listOf(
    "Glass can be recycled again and again without losing quality.",
    "Recycling aluminium uses far less energy than making new metal from ore.",
    "Batteries and e-waste contain metals that can leak into soil and water if they go in the normal bin.",
    "Composting food waste keeps it out of landfills, where it would produce methane.",
    "Rinsing and drying recyclables helps collectors accept them.",
    "Plastic bottles are usually PET (1) or HDPE (2), the plastics that are most widely recycled."
)

private fun greeting(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

/** The first screen: brand header, big Scan button, how it works, the four bins, a daily fact. */
@Composable
fun HomeScreen(
    onScan: () -> Unit,
    onLearn: (ChatRequest) -> Unit
) {
    val ctx = LocalContext.current
    val fact = remember { FACTS[((System.currentTimeMillis() / 86_400_000L) % FACTS.size).toInt()] }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF0B3D2A), Color(0xFF2E9E6B))))
                .drawBehind {
                    drawCircle(Color(0x16FFFFFF), 150.dp.toPx(), Offset(size.width * 0.95f, 0f))
                    drawCircle(Color(0x10FFFFFF), 100.dp.toPx(), Offset(size.width * 0.05f, size.height))
                }
                .statusBarsPadding()
                .padding(start = 24.dp, end = 20.dp, top = 28.dp, bottom = 72.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    greeting(),
                    color = Color(0xCCFFFFFF),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.height(4.dp))
                Text("Waste Assistant", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Scan an item and learn how to dispose of it the right way.",
                    color = Color(0xE6FFFFFF),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Icon24("recycle", Color(0x40FFFFFF), 88.dp)
        }

        // Content overlapping the header
        Column(
            modifier = Modifier
                .offset(y = (-44).dp)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onScan,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon24("camera", Color.White, 22.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Scan waste", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Take a photo, or pick one from your gallery on the next screen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF55605A)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat("4", "Categories")
                Stat("8", "Item types")
                Stat("0", "Cloud calls")
            }

            // How it works
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("How it works", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HowStep("1", "Scan", "Point at an item")
                        HowStep("2", "Sort", "See the right bin")
                        HowStep("3", "Learn", "Get steps and answers")
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Know your bins", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Tap a category to learn more.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF55605A)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (rowIndex in 0 until 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (colIndex in 0 until 2) {
                            val (category, example) = BIN_EXAMPLES[rowIndex * 2 + colIndex]
                            CategoryCard(category, example) {
                                try {
                                    val info = KnowledgeBase.get(ctx, category)
                                    onLearn(ChatRequest(info.toContext(), info.title, info.category))
                                } catch (e: Exception) {
                                    onScan()
                                }
                            }
                        }
                    }
                }
            }

            // Did you know
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF1B6B43), Color(0xFF2E9E6B))))
                    .padding(20.dp)
            ) {
                Row {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0x33FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon24("bulb", Color.White, 24.dp)
                    }
                    Column(
                        modifier = Modifier.padding(start = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Did you know?", style = MaterialTheme.typography.titleSmall, color = Color(0xCCFFFFFF))
                        Text(fact, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    }
                }
            }

            // Trust badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Pill("Private")
                Pill("Works offline")
                Pill("On-device AI")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RowScope.HowStep(number: String, title: String, caption: String) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0xFF1B6B43)),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            caption,
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF55605A),
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RowScope.CategoryCard(category: String, example: String, onClick: () -> Unit) {
    val accent = categoryColor(category)
    Card(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon24(categoryIcon(category), accent, 26.dp)
            }
            Text(category, style = MaterialTheme.typography.titleMedium, color = Color(0xFF14532D))
            Text(
                example,
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF55605A),
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
private fun RowScope.Stat(value: String, label: String) {
    Card(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B6B43))
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color(0xFF55605A))
        }
    }
}

@Composable
private fun Pill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = Color(0xFF24362B))
    }
}