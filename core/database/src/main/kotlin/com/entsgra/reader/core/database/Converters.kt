package com.entsgra.reader.core.database

import androidx.room.TypeConverter
import com.entsgra.reader.core.model.Locator

internal class Converters {
	@TypeConverter
	fun locatorToString(locator: Locator): String = locator.encode()

	@TypeConverter
	fun stringToLocator(value: String): Locator = Locator.decode(value) ?: Locator.Page(0)
}
