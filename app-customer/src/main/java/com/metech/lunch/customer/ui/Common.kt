package com.metech.lunch.customer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.metech.lunch.core.model.MenuItem
import com.metech.lunch.core.ui.shimmer

/** A meal's photo, or a warm gradient tile with a matching emoji when the kitchen hasn't added one. */
@Composable
fun MealArt(
    name: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    emojiSize: TextUnit = 56.sp,
) {
    val (emoji, colors) = mealLook(name)
    Box(modifier.background(Brush.linearGradient(colors)), contentAlignment = Alignment.Center) {
        if (imageUrl != null) {
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                loading = { Box(Modifier.matchParentSize().shimmer(androidx.compose.ui.graphics.RectangleShape)) },
                error = { Text(emoji, fontSize = emojiSize) },
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Text(emoji, fontSize = emojiSize)
        }
    }
}

fun MenuItem.isVeg(): Boolean =
    dietaryTags.none { it.equals("non-veg", true) || it.equals("nonveg", true) || it.equals("egg", true) }

private val looks = listOf(
    listOf("biryani", "pulao") to ("🍗" to listOf(Color(0xFFFFD8A8), Color(0xFFF2994A))),
    listOf("thali", "meal") to ("🍛" to listOf(Color(0xFFFFE9C7), Color(0xFFF6B26B))),
    listOf("curd", "rice", "khichdi") to ("🍚" to listOf(Color(0xFFF3F0E6), Color(0xFFD9CBB0))),
    listOf("paneer", "tikka") to ("🧆" to listOf(Color(0xFFFFE1C2), Color(0xFFE88B4A))),
    listOf("roti", "paratha", "naan", "wrap") to ("🫓" to listOf(Color(0xFFFCEBD0), Color(0xFFDDB27C))),
    listOf("salad", "bowl", "sprout") to ("🥗" to listOf(Color(0xFFDDF2DF), Color(0xFF8CC79A))),
    listOf("dosa", "idli", "upma") to ("🥞" to listOf(Color(0xFFFFF0CF), Color(0xFFE8B964))),
    listOf("noodle", "pasta", "hakka") to ("🍜" to listOf(Color(0xFFFFE3CF), Color(0xFFE59866))),
    listOf("sweet", "kheer", "dessert", "halwa") to ("🍮" to listOf(Color(0xFFFFE6EC), Color(0xFFE79AAF))),
    listOf("chicken", "mutton", "fish", "egg") to ("🍗" to listOf(Color(0xFFFFDCC9), Color(0xFFD9754A))),
)

private fun mealLook(name: String): Pair<String, List<Color>> {
    val lower = name.lowercase()
    return looks.firstOrNull { (keys, _) -> keys.any { it in lower } }?.second
        ?: ("🍽️" to listOf(Color(0xFFFFEAD2), Color(0xFFF2B27A)))
}

/** Simple top bar for inner screens: back arrow, serif title, optional trailing content. */
@Composable
fun ScreenTopBar(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface),
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Spacer(Modifier.width(8.dp))
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun Avatar(name: String, size: Dp = 44.dp, onClick: (() -> Unit)? = null) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "🙂"
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFFF2994A), Color(0xFFD9752B))))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
    }
}
