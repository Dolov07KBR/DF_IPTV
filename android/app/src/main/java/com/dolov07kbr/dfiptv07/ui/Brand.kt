package com.dolov07kbr.dfiptv07.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dolov07kbr.dfiptv07.R

/** Фирменные токены сайта dolov07kbr.github.io. */
object DfBrand {
    val Bg = Color(0xFF0B1020)
    val Bg2 = Color(0xFF0E1630)
    val Card = Color(0xFF121A35)
    val CardDim = Color(0xDD121A35)
    val Line = Color(0x408B5CF6)
    val Text = Color(0xFFE7ECFF)
    val Muted = Color(0xFF93A0C8)
    val Violet = Color(0xFF8B5CF6)
    val Cyan = Color(0xFF22D3EE)
    val Star = Color(0xFFFFD54F)
    val Grad: Brush = Brush.horizontalGradient(listOf(Violet, Cyan))
    val ShapeL = RoundedCornerShape(22.dp)
    val ShapeM = RoundedCornerShape(16.dp)
    val ShapeS = RoundedCornerShape(12.dp)
}

@Composable
fun DfTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = DfBrand.Violet,
            onPrimary = Color.White,
            primaryContainer = Color(0xFF283477),
            onPrimaryContainer = DfBrand.Text,
            secondary = DfBrand.Cyan,
            onSecondary = Color(0xFF06202A),
            background = DfBrand.Bg,
            onBackground = DfBrand.Text,
            surface = DfBrand.Card,
            onSurface = DfBrand.Text,
            surfaceVariant = DfBrand.Bg2,
            onSurfaceVariant = DfBrand.Muted,
            outline = DfBrand.Line,
            error = Color(0xFFFF6B81),
        ),
        content = content,
    )
}

/** Текст с фирменным градиентом фиолетовый → циан, как на сайте. */
@Composable
fun GradientText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
        fontWeight = FontWeight.ExtraBold,
        brush = DfBrand.Grad,
    )
}

/** Логотип DF как на сайте (assets/logo.png). */
@Composable
fun BrandLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.df_logo),
        contentDescription = "Логотип DF",
        modifier = modifier.clip(RoundedCornerShape(22.dp)),
    )
}

/** Плашка-подложка с градиентом для пустых состояний и шапок. */
@Composable
fun GradSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .clip(DfBrand.ShapeL)
            .background(DfBrand.Bg2)
            .background(Brush.radialGradient(listOf(Color(0x558B5CF6), Color(0x008B5CF6)))),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun MutedText(text: String, modifier: Modifier = Modifier) {
    Text(text = text, modifier = modifier, color = DfBrand.Muted, style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        modifier = modifier.padding(bottom = 4.dp),
        color = DfBrand.Text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
}
