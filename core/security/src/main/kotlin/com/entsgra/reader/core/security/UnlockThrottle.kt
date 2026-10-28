package com.entsgra.reader.core.security

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wait after [failures] wrong secrets in a row: none for the first four, then 30 seconds,
 * doubling with each further failure up to an hour. Nothing is ever wiped.
 */
fun lockoutMs(failures: Int): Long {
	if (failures < FREE_ATTEMPTS + 1) return 0
	val doublings = (failures - FREE_ATTEMPTS - 1).coerceAtMost(MAX_DOUBLINGS)
	return (FIRST_LOCKOUT_MS shl doublings).coerceAtMost(MAX_LOCKOUT_MS)
}

private const val FREE_ATTEMPTS = 4
private const val FIRST_LOCKOUT_MS = 30_000L
private const val MAX_LOCKOUT_MS = 60 * 60_000L
private const val MAX_DOUBLINGS = 16

interface ThrottleClock {
	fun wallMs(): Long

	/** Time since boot; does not jump when the clock is changed. */
	fun elapsedMs(): Long

	/** Changes on every reboot, when elapsed time starts again from zero. */
	fun bootCount(): Int
}

/**
 * Failed attempts per secret ([key]: a folder, or the app), kept in no-backup storage so a
 * restart does not reset them. The wait is measured on both clocks: within one boot the
 * elapsed clock holds it however the wall clock is set; across a reboot the wall clock does,
 * capped at the full wait so setting the clock back cannot stretch it.
 */
@Singleton
class UnlockThrottle internal constructor(
	private val file: File,
	private val clock: ThrottleClock,
) {
	@Inject
	constructor(@ApplicationContext context: Context) : this(
		File(context.noBackupFilesDir, FILE_NAME),
		AndroidThrottleClock(context),
	)

	/** Milliseconds before the next attempt may be made. */
	@Synchronized
	fun waitMs(key: String): Long {
		val props = load()
		val failures = props.getProperty("$key.failures")?.toIntOrNull() ?: return 0
		val delay = lockoutMs(failures)
		if (delay == 0L) return 0
		val wall = props.getProperty("$key.wall")?.toLongOrNull() ?: return delay
		val elapsed = props.getProperty("$key.elapsed")?.toLongOrNull()
		val boot = props.getProperty("$key.boot")?.toIntOrNull()
		val wallLeft = (wall + delay - clock.wallMs()).coerceIn(0, delay)
		val elapsedLeft = if (elapsed != null && boot == clock.bootCount()) {
			(elapsed + delay - clock.elapsedMs()).coerceIn(0, delay)
		} else {
			0
		}
		return maxOf(wallLeft, elapsedLeft)
	}

	/** Records a wrong secret. Returns the wait it starts. */
	@Synchronized
	fun failed(key: String): Long {
		val props = load()
		val failures = (props.getProperty("$key.failures")?.toIntOrNull() ?: 0) + 1
		props.setProperty("$key.failures", failures.toString())
		props.setProperty("$key.wall", clock.wallMs().toString())
		props.setProperty("$key.elapsed", clock.elapsedMs().toString())
		props.setProperty("$key.boot", clock.bootCount().toString())
		save(props)
		return lockoutMs(failures)
	}

	@Synchronized
	fun succeeded(key: String) = forget(key)

	@Synchronized
	fun forget(key: String) {
		val props = load()
		val names = props.stringPropertyNames().filter { it.startsWith("$key.") }
		if (names.isEmpty()) return
		names.forEach(props::remove)
		save(props)
	}

	private fun load(): Properties = Properties().apply {
		if (file.exists()) runCatching { file.inputStream().use(::load) }
	}

	private fun save(props: Properties) {
		file.parentFile?.mkdirs()
		val temp = File(file.parentFile, "${file.name}.part")
		writeDurably(temp) { props.store(it, null) }
		check(temp.renameTo(file)) { "Could not store unlock attempts" }
	}

	private companion object {
		const val FILE_NAME = "unlock_attempts"
	}
}

private class AndroidThrottleClock(private val context: Context) : ThrottleClock {
	override fun wallMs() = System.currentTimeMillis()

	override fun elapsedMs() = SystemClock.elapsedRealtime()

	override fun bootCount() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
}
