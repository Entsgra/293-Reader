package com.entsgra.reader.core.model

data class PhysicalBook(
	val id: Long,
	val title: String,
	val author: String?,
	val pageCount: Int?,
	val isbn: String?,
	val status: ReadingStatus,
	val currentPage: Int,
	val addedAt: Long,
	val updatedAt: Long,
)

enum class ReadingStatus { WANT_TO_READ, READING, FINISHED, ABANDONED }
