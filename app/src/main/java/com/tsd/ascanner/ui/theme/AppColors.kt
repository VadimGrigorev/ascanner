package com.tsd.ascanner.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class AppColors(
    val background: Color,     // rgb(247, 250, 255) — фон приложения (Scaffold)
    val textPrimary: Color,    // rgb(17, 20, 23) — заголовки (списки, карточки)
    val textSecondary: Color,  // rgb(91, 107, 120) — подзаголовки/счётчики
    val primary: Color,        // rgb(30, 136, 229) — primary‑кнопки (Импорт JSON)
    val onPrimary: Color,      // rgb(255, 255, 255) — текст/иконки на primary‑кнопках
    val secondary: Color,      // rgb(172, 172, 172) — вторичные кнопки (Экспорт JSON)
    val progress: Color,       // rgb(52, 199, 89) — прогресс для завершённых
    val progressTodo: Color,   // rgb(199, 203, 209) — прогресс для незавершённых
    val progressTrack: Color,  // rgb(229, 231, 235) — трек полосы прогресса
	val statusDoneBg: Color,   // фон карточек завершённых задач/заказов
	val statusTodoBg: Color,   // фон карточек незавершённых задач/заказов (open)
	val statusPendingBg: Color, // фон карточек в ожидании (pending)
	val statusWarningBg: Color, // фон карточек с предупреждением (warning)
	val statusErrorBg: Color   // фон карточек с ошибкой (error)
)

val LocalAppColors = staticCompositionLocalOf {
    AppColors(
        background = Color(0xFFF7FAFF),   // rgb(247, 250, 255) — Scaffold.containerColor
        textPrimary = Color(0xFF111417),  // rgb(17, 20, 23) — заголовки
        textSecondary = Color(0xFF5B6B78),// rgb(91, 107, 120) — подзаголовки/счётчики
        primary = Color(0xFF1E88E5),      // rgb(30, 136, 229) — основная кнопка (Импорт)
        onPrimary = Color(0xFFFFFFFF),    // rgb(255, 255, 255) — текст на основной кнопке
        secondary = Color(0xFFACACAC),    // rgb(172, 172, 172) — вторичная кнопка (Экспорт)
        progress = Color(0xFF34C759),     // rgb(52, 199, 89) — прогресс завершённых
        progressTodo = Color(0xFFC7CBD1), // rgb(199, 203, 209) — прогресс незавершённых
        progressTrack = Color(0xFFE5E7EB),// rgb(229, 231, 235) — трек прогресса
		statusDoneBg = Color(0xFFE7F7EC),
		statusTodoBg = Color(30, 136, 229),
		statusPendingBg = Color(0xFFCDD1C4),
		statusWarningBg = Color(0xFFFFF59D),
		statusErrorBg = Color(0xFFFF595E)
    )
}

object AppPalettes {
    val Light = AppColors(
        background = Color(0xFFF7FAFF),   // rgb(247, 250, 255) — Scaffold
        textPrimary = Color(0xFF111417),  // rgb(17, 20, 23) — заголовки
        textSecondary = Color(50, 50, 50),// rgb(17, 20, 23) — подзаголовки
        primary = Color(0xFF1E88E5),      // rgb(30, 136, 229) — важные CTA
        onPrimary = Color(0xFFFFFFFF),    // rgb(255, 255, 255) — текст на CTA
        secondary = Color(0xFFACACAC),    // rgb(172, 172, 172) — вторичные действия
        progress = Color(0xFF34C759),     // rgb(52, 199, 89) — прогресс OK
        progressTodo = Color(0xFFC7CBD1), // rgb(199, 203, 209) — прогресс TODO
        progressTrack = Color(0xFFE5E7EB),// rgb(229, 231, 235) — трек прогресса
		statusDoneBg = Color(52, 199, 89),
		statusTodoBg = Color(30, 136, 229),
		statusPendingBg = Color(0xFFCDD1C4),
		statusWarningBg = Color(0xFFFFF59D),
		statusErrorBg = Color(0xFFFF595E)
    )
}

object AppTheme {
    val colors: AppColors
        @androidx.compose.runtime.Composable get() = LocalAppColors.current
}

