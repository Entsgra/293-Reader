package com.entsgra.reader.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.entsgra.reader.core.designsystem.theme.ReaderColors
import com.entsgra.reader.core.designsystem.theme.readerColors
import com.entsgra.reader.core.model.AppTheme
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/** Fails when ColorTokens.kt drifts from design/293-tokens.json. */
class ColorTokensTest {
	private val tokensFile = generateSequence(File("").absoluteFile) { it.parentFile }
		.map { File(it, "design/293-tokens.json") }
		.first { it.exists() }

	private val roles: Map<String, (ReaderColors) -> Color> = mapOf(
		"fog" to { it.fog },
		"fog-raised" to { it.fogRaised },
		"ledge" to { it.ledge },
		"rib" to { it.rib },
		"ink" to { it.ink },
		"ink-muted" to { it.inkMuted },
		"action" to { it.action },
		"on-action" to { it.onAction },
		"banner" to { it.banner },
		"on-banner" to { it.onBanner },
	)

	private val themeIds = mapOf(
		"light" to AppTheme.LIGHT,
		"dark" to AppTheme.DARK,
		"yellow" to AppTheme.YELLOW,
		"pink" to AppTheme.PINK,
	)

	@Test
	fun everyColourTokenMatchesTheSpec() {
		val json = tokensFile.readText()
		val tokenBlock = Regex(""""name":\s*"([a-z-]+)",\s*"value":\s*\{([^}]*)}""")
		val themeValue = Regex(""""(\w+)":\s*"(#[0-9a-fA-F]{6})"""")
		var checked = 0
		tokenBlock.findAll(json).forEach { block ->
			val name = block.groupValues[1]
			val read = roles[name] ?: return@forEach
			themeValue.findAll(block.groupValues[2]).forEach { value ->
				val theme = themeIds.getValue(value.groupValues[1])
				val expected = 0xFF000000.toInt() or value.groupValues[2].drop(1).toInt(16)
				assertWithMessage("$name / $theme")
					.that(Integer.toHexString(read(readerColors(theme)).toArgb()))
					.isEqualTo(Integer.toHexString(expected))
				checked++
			}
		}
		assertWithMessage("token values checked").that(checked).isEqualTo(roles.size * themeIds.size)
	}
}
