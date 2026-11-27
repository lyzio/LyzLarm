package se.lyzio.anatole

import java.time.ZonedDateTime

/**
 * "Now" for the screens and the next-alarm calculation. Always the real clock in the app;
 * the store screenshot test (StoreScreenshots) fixes it, so the pictures show a believable
 * bedtime and come out the same on every run.
 */
object AppClock {
    @Volatile var fixed: Long? = null

    fun now(): Long = fixed ?: System.currentTimeMillis()

    fun zoned(): ZonedDateTime = fixed?.let { ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), java.time.ZoneId.systemDefault()) }
        ?: ZonedDateTime.now()
}
