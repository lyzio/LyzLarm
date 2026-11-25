package se.lyzio.lyzlarm.tracking

import android.content.Context
import se.lyzio.lyzlarm.app
import se.lyzio.lyzlarm.data.EmptyBedTest
import se.lyzio.lyzlarm.data.EpochRow
import se.lyzio.lyzlarm.engine.EmptyBedReference
import se.lyzio.lyzlarm.engine.Point

/** Bridges between the database rows and the pure sleep engine. */
fun EpochRow.toPoint() = Point(time, movement, noise)

fun EmptyBedTest.toReference() = EmptyBedReference(noiseP50, noiseP90, movementMax, epochCount)

/** The newest empty bed test as a reference for out-of-bed detection, or null if never run. */
suspend fun emptyBedReference(context: Context): EmptyBedReference? =
    context.app.database.extrasDao().latestEmptyBedTest()?.toReference()
