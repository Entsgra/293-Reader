package com.entsgra.reader.core.model

/** Last read position. Kept apart from [Book] so page turns do not invalidate library queries. */
data class ReadingPosition(
	val bookId: Long,
	val locator: Locator,
	val progress: Float,
	val updatedAt: Long,
)
