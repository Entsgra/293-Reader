package com.entsgra.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.entsgra.reader.core.designsystem.theme.ReaderTheme
import com.entsgra.reader.core.designsystem.theme.readerColors
import com.entsgra.reader.core.designsystem.theme.systemBarStyle
import com.entsgra.reader.navigation.ReaderApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
	private val viewModel: MainViewModel by viewModels()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContent {
			val theme by viewModel.theme.collectAsStateWithLifecycle()
			val current = theme ?: return@setContent
			DisposableEffect(current) {
				val style = systemBarStyle(readerColors(current))
				enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
				onDispose {}
			}
			ReaderTheme(current) {
				ReaderApp()
			}
		}
	}
}
