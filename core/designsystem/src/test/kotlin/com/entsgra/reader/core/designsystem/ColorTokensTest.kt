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

	private val colorsXml = generateSequence(File("").absoluteFile) { it.parentFile }
		.map { File(it, "core/designsystem/src/main/res/values/colors.xml") }
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

	@Test
	fun windowFogInXmlMatchesTheSpec() {
		val xml = colorsXml.readText()
		themeIds.forEach { (id, theme) ->
			val value = Regex("""<color name="reader_fog_$id">#([0-9A-Fa-f]{6})</color>""").find(xml)
			assertWithMessage("reader_fog_$id in colors.xml").that(value).isNotNull()
			val expected = 0xFF000000.toInt() or value!!.groupValues[1].toInt(16)
			assertWithMessage("reader_fog_$id")
				.that(Integer.toHexString(readerColors(theme).fog.toArgb()))
				.isEqualTo(Integer.toHexString(expected))
		}
	}
}
