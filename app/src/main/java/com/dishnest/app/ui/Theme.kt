package com.dishnest.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Sage = Color(0xFF456B59)
val Ivory = Color(0xFFFAF7F0)
val Cream = Color(0xFFF1E8D8)
val Ink = Color(0xFF29312D)
val Terra = Color(0xFF9E5034)
val Muted = Color(0xFF626B62)
val Line = Color(0xFFE4E6DD)

@Composable
fun DishNestTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme =
            lightColorScheme(
                primary = Sage,
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE5EDE2),
                onPrimaryContainer = Ink,
                secondary = Terra,
                secondaryContainer = Cream,
                onSecondaryContainer = Ink,
                tertiary = Sage,
                tertiaryContainer = Color(0xFFE5EDE2),
                background = Ivory,
                surface = Color.White,
                onSurface = Ink,
                onBackground = Ink,
                outline = Line,
            ),
        typography =
            Typography(
                displaySmall =
                    TextStyle(fontFamily = FontFamily.Serif, fontSize = 36.sp, lineHeight = 42.sp),
                headlineLarge =
                    TextStyle(fontFamily = FontFamily.Serif, fontSize = 32.sp, lineHeight = 39.sp),
                headlineMedium =
                    TextStyle(fontFamily = FontFamily.Serif, fontSize = 27.sp, lineHeight = 34.sp),
                titleLarge =
                    TextStyle(fontFamily = FontFamily.Serif, fontSize = 23.sp, lineHeight = 29.sp),
                titleMedium =
                    TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 23.sp,
                    ),
                bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
                bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
                labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
            ),
        content = content,
    )
}
