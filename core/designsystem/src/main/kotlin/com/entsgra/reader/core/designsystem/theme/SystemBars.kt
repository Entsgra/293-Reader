package com.entsgra.reader.core.designsystem.theme

import android.graphics.Color
import androidx.activity.SystemBarStyle

/** Transparent system bars so `fog` runs edge to edge; icon contrast follows the theme. */
fun systemBarStyle(colors: ReaderColors): SystemBarStyle =
	if (colors.isDark) {
		SystemBarStyle.dark(Color.TRANSPARENT)
	} else {
		SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
	}
