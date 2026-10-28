package com.entsgra.reader.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.entsgra.reader.core.designsystem.R

/** Jost (OFL 1.1), bundled as one variable font. The only typeface in the app. */
val Jost = FontFamily(
	Font(
		R.font.jost,
		weight = FontWeight.Normal,
		variationSettings = FontVariation.Settings(FontVariation.weight(400)),
	),
	Font(
		R.font.jost,
		weight = FontWeight.SemiBold,
		variationSettings = FontVariation.Settings(FontVariation.weight(600)),
	),
)

/** The 293 type scale. Sizes are sp so text follows dynamic type. */
@Immutable
data class ReaderType(
	/** Screen title, uppercase. */
	val display: TextStyle,
	/** The number a screen exists for. Always under a label. */
	val figure: TextStyle,
	/** Times and dates. */
	val time: TextStyle,
	/** Section titles, uppercase. */
	val headline: TextStyle,
	/** Row and card titles. */
	val title: TextStyle,
	/** Running text, sentence case. */
	val body: TextStyle,
	/** Buttons, chips, field labels, nav. Uppercase. */
	val label: TextStyle,
	/** Nav labels, month under a date. Uppercase, never sentences. */
	val labelSmall: TextStyle,
)

private fun jost(size: Int, line: Int, weight: Int, tracking: Double = 0.0) = TextStyle(
	fontFamily = Jost,
	fontSize = size.sp,
	lineHeight = line.sp,
	fontWeight = FontWeight(weight),
	letterSpacing = tracking.em,
)

val ReaderTypography = ReaderType(
	display = jost(size = 40, line = 42, weight = 600, tracking = 0.04),
	figure = jost(size = 72, line = 68, weight = 600, tracking = 0.01),
	time = jost(size = 26, line = 32, weight = 600, tracking = 0.02),
	headline = jost(size = 22, line = 28, weight = 600, tracking = 0.06),
	title = jost(size = 17, line = 24, weight = 600),
	body = jost(size = 16, line = 24, weight = 400),
	label = jost(size = 14, line = 20, weight = 600, tracking = 0.1),
	labelSmall = jost(size = 11, line = 14, weight = 600, tracking = 0.1),
)
