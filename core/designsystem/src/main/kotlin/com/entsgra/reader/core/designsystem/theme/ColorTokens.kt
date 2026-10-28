package com.entsgra.reader.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.entsgra.reader.core.model.AppTheme

/** The 293 colour roles. Values mirror design/293-tokens.json; ColorTokensTest keeps them in sync. */
@Immutable
data class ReaderColors(
	/** Screen ground. */
	val fog: Color,
	/** Terrace faces: cards, inputs, secondary buttons, the bottom bar. */
	val fogRaised: Color,
	/** Underside of a terrace; pressed, disabled and full fills. */
	val ledge: Color,
	/** Row rules, control outlines, the Window plate divider. */
	val rib: Color,
	val ink: Color,
	val inkMuted: Color,
	/** The only action colour. */
	val action: Color,
	val onAction: Color,
	/** The one faded red. One per screen. */
	val banner: Color,
	val onBanner: Color,
	val isDark: Boolean,
)

val LightColors = ReaderColors(
	fog = Color(0xFFD9D9D4),
	fogRaised = Color(0xFFE8E8E4),
	ledge = Color(0xFFBCBCB6),
	rib = Color(0xFF4A4C4D),
	ink = Color(0xFF18191A),
	inkMuted = Color(0xFF4A4C4D),
	action = Color(0xFF18191A),
	onAction = Color(0xFFE8E8E4),
	banner = Color(0xFF9F3426),
	onBanner = Color(0xFFF1EFE9),
	isDark = false,
)

val DarkColors = ReaderColors(
	fog = Color(0xFF141516),
	fogRaised = Color(0xFF212325),
	ledge = Color(0xFF34373A),
	rib = Color(0xFF8D8F8F),
	ink = Color(0xFFDDDCD6),
	inkMuted = Color(0xFFA2A4A4),
	action = Color(0xFFDDDCD6),
	onAction = Color(0xFF141516),
	banner = Color(0xFFE3725F),
	onBanner = Color(0xFF141516),
	isDark = true,
)

val YellowColors = ReaderColors(
	fog = Color(0xFFE4D9AE),
	fogRaised = Color(0xFFF0E8C6),
	ledge = Color(0xFFC8BB88),
	rib = Color(0xFF4E4934),
	ink = Color(0xFF1C1A12),
	inkMuted = Color(0xFF4E4934),
	action = Color(0xFF1C1A12),
	onAction = Color(0xFFF0E8C6),
	banner = Color(0xFF952C1D),
	onBanner = Color(0xFFF6F0E2),
	isDark = false,
)

val PinkColors = ReaderColors(
	fog = Color(0xFFE7D2CE),
	fogRaised = Color(0xFFF2E4E1),
	ledge = Color(0xFFCCB0AA),
	rib = Color(0xFF523E3B),
	ink = Color(0xFF1D1517),
	inkMuted = Color(0xFF523E3B),
	action = Color(0xFF1D1517),
	onAction = Color(0xFFF2E4E1),
	banner = Color(0xFF8E2618),
	onBanner = Color(0xFFF7ECE9),
	isDark = false,
)

fun readerColors(theme: AppTheme): ReaderColors = when (theme) {
	AppTheme.LIGHT -> LightColors
	AppTheme.DARK -> DarkColors
	AppTheme.YELLOW -> YellowColors
	AppTheme.PINK -> PinkColors
}
