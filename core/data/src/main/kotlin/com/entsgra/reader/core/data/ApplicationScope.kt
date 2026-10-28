package com.entsgra.reader.core.data

import javax.inject.Qualifier

/** A scope that lives as long as the process, for work that must outlast a screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
