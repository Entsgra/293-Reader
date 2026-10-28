package com.entsgra.reader.reader.api

/** Why a book could not be opened, in terms the reader screen can turn into one sentence. */
class BookOpenException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
	enum class Reason {
		/** No reader for this format yet. */
		UNSUPPORTED,

		/** Password protected or DRM: the file must be unlocked elsewhere first. */
		PROTECTED,

		/** The file is cut short or corrupt. */
		DAMAGED,

		/** Opens, but has nothing to show (a comic archive with no images). */
		EMPTY,
	}
}
