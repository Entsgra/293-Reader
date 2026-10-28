package com.entsgra.reader.core.designsystem.theme

import android.graphics.Color
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StyleRes
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import com.entsgra.reader.core.designsystem.R
import com.entsgra.reader.core.model.AppTheme

/** Transparent system bars so `fog` runs edge to edge; icon contrast follows the theme. */
fun systemBarStyle(colors: ReaderColors): SystemBarStyle =
	if (colors.isDark) {
		SystemBarStyle.dark(Color.TRANSPARENT)
	} else {
		SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
	}

/** The XML window theme whose background is [theme]'s `fog`. */
@StyleRes
fun windowThemeRes(theme: AppTheme): Int = when (theme) {
	AppTheme.LIGHT -> R.style.Theme_Reader_Light
	AppTheme.DARK -> R.style.Theme_Reader_Dark
	AppTheme.YELLOW -> R.style.Theme_Reader_Yellow
	AppTheme.PINK -> R.style.Theme_Reader_Pink
}

/**
 * Paints everything outside Compose for [theme]: system bar icon contrast, the window
 * background, and on API 33+ the splash screen the system shows on the next cold start.
 */
fun ComponentActivity.applyReaderWindow(theme: AppTheme) {
	val colors = readerColors(theme)
	val style = systemBarStyle(colors)
	enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
	window.setBackgroundDrawable(colors.fog.toArgb().toDrawable())
	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
		splashScreen.setSplashScreenTheme(windowThemeRes(theme))
	}
}
