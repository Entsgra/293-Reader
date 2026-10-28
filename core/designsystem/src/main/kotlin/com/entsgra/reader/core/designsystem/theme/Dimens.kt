package com.entsgra.reader.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Spacing tokens from 293-tokens.json. */
object ReaderSpacing {
	/** Icon-to-label gap. */
	val space1 = 4.dp
	/** Gap inside chips and between stacked labels. */
	val space2 = 8.dp
	/** Gap between sibling controls. */
	val space3 = 12.dp
	/** Screen side margin on phones; card padding. */
	val space4 = 16.dp
	/** Horizontal button padding. */
	val space5 = 20.dp
	/** Gap between content groups. */
	val space6 = 24.dp
	/** Gap between screen sections. */
	val space8 = 32.dp
	/** Air above a monumental title. */
	val space12 = 48.dp
}

/** Fixed dimensions and strokes: the size tokens plus the values the design doc states in prose. */
object ReaderSize {
	val targetMin = 48.dp
	val barHeight = 64.dp
	val rowHeight = 56.dp
	/** Action edge along the top of the key card and the active tab. */
	val strokeTrim = 4.dp
	/** How far a terrace ledge steps out below its face. */
	val ledgeStep = 8.dp
	/** Window plate width, divider included. */
	val plateWidth = 88.dp

	/** A pressed terrace drops this far onto its ledge. */
	val pressDrop = 4.dp
	/** Row rules and the rule above an inline control. */
	val rule = 1.dp
	/** Control outlines (chips, secondary buttons, switch track). */
	val outline = 1.dp
	/** Divider inside a Window plate; destructive button outline; error outline. */
	val heavyRule = 2.dp
	val focusRing = 3.dp
	val focusOffset = 2.dp
	val statusDot = 10.dp
	val errorDisc = 24.dp
	val icon = 24.dp

	val switchTrackWidth = 44.dp
	val switchTrackHeight = 24.dp
	val switchKnob = 14.dp
}

object ReaderShapes {
	/** Everything by default. */
	val none: Shape = RectangleShape
	/** The one sweep per screen: bottom-right of the key card. */
	val sweep: Shape = RoundedCornerShape(bottomEnd = 120.dp)
	/** Step markers, the FAB, the switch knob, status dots. */
	val round: Shape = CircleShape
}

object ReaderMotion {
	const val DURATION_MS = 120
	/** CSS ease-out. Terraces press down; nothing slides sideways or bounces. */
	val easing: Easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)
}
