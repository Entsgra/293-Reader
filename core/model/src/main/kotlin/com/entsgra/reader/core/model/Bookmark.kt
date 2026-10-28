package com.entsgra.reader.core.model

data class Bookmark(
	val id: Long,
	val bookId: Long,
	val locator: Locator,
	val label: String?,
	val createdAt: Long,
)
