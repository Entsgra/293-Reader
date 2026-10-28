package com.entsgra.reader.core.designsystem.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import com.entsgra.reader.core.model.AppTheme

val LocalReaderColors = staticCompositionLocalOf { LightColors }
val LocalReaderType = staticCompositionLocalOf { ReaderTypography }

/**
 * The 293 theme. Provides [ReaderColors] and [ReaderType], and maps them onto Material 3 per the
 * design doc's Compose section so stock components land on the right roles. Ripples are replaced
 * by a focus ring; press feedback belongs to terraces.
 */
@Composable
fun ReaderTheme(
	theme: AppTheme = AppTheme.LIGHT,
	content: @Composable () -> Unit,
) {
	val colors = readerColors(theme)
	val scheme = remember(colors) { colors.toColorScheme() }
	val indication = remember(colors) { FocusRingIndication(colors.action) }
	CompositionLocalProvider(
		LocalReaderColors provides colors,
		LocalReaderType provides ReaderTypography,
	) {
		MaterialTheme(
			colorScheme = scheme,
			typography = MaterialTypography,
			shapes = SquareShapes,
		) {
			CompositionLocalProvider(
				LocalIndication provides indication,
				LocalContentColor provides colors.ink,
				LocalTextStyle provides ReaderTypography.body,
				content = content,
			)
		}
	}
}

object ReaderTheme {
	val colors: ReaderColors
		@Composable @ReadOnlyComposable get() = LocalReaderColors.current

	val type: ReaderType
		@Composable @ReadOnlyComposable get() = LocalReaderType.current
}

/** fog → background, fog-raised → surface, action → primary, banner → error. */
private fun ReaderColors.toColorScheme(): ColorScheme {
	val base = if (isDark) darkColorScheme() else lightColorScheme()
	return base.copy(
		primary = action,
		onPrimary = onAction,
		primaryContainer = action,
		onPrimaryContainer = onAction,
		inversePrimary = onAction,
		secondary = action,
		onSecondary = onAction,
		secondaryContainer = ledge,
		onSecondaryContainer = ink,
		tertiary = action,
		onTertiary = onAction,
		tertiaryContainer = ledge,
		onTertiaryContainer = ink,
		background = fog,
		onBackground = ink,
		surface = fogRaised,
		onSurface = ink,
		surfaceVariant = ledge,
		onSurfaceVariant = inkMuted,
		surfaceTint = fogRaised,
		inverseSurface = action,
		inverseOnSurface = onAction,
		error = banner,
		onError = onBanner,
		errorContainer = banner,
		onErrorContainer = onBanner,
		outline = rib,
		outlineVariant = rib,
		scrim = action,
		surfaceBright = fogRaised,
		surfaceDim = fog,
		surfaceContainerLowest = fogRaised,
		surfaceContainerLow = fogRaised,
		surfaceContainer = fogRaised,
		surfaceContainerHigh = fogRaised,
		surfaceContainerHighest = fogRaised,
	)
}

private val MaterialTypography = Typography(
	displayLarge = ReaderTypography.figure,
	displayMedium = ReaderTypography.display,
	displaySmall = ReaderTypography.display,
	headlineLarge = ReaderTypography.headline,
	headlineMedium = ReaderTypography.headline,
	headlineSmall = ReaderTypography.time,
	titleLarge = ReaderTypography.headline,
	titleMedium = ReaderTypography.title,
	titleSmall = ReaderTypography.title,
	bodyLarge = ReaderTypography.body,
	bodyMedium = ReaderTypography.body,
	bodySmall = ReaderTypography.body,
	labelLarge = ReaderTypography.label,
	labelMedium = ReaderTypography.label,
	labelSmall = ReaderTypography.labelSmall,
)

private val SquareShapes = RoundedCornerShape(0.dp).let { square ->
	Shapes(
		extraSmall = square,
		small = square,
		medium = square,
		large = square,
		extraLarge = square,
	)
}
