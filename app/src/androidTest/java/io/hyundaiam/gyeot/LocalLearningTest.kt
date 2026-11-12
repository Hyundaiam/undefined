package io.hyundaiam.gyeot

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LocalLearningTest {
    private val context get() = ApplicationProvider.getApplicationContext<GyeotApplication>()
    @Test fun driverProfilesAndDemoDataRemainSeparatedAfterDatabaseReopen() = runBlocking {
        val name = "learning-test-${UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, GyeotDatabase::class.java, name).build()
        try {
            val dao = db.dao()
            val a = dao.addDriver(DriverEntity(name = "A")); val b = dao.addDriver(DriverEntity(name = "B"))
            val real = dao.addTrip(TripEntity(driverId = a, source = "PLEOS", startedAt = 1000, lastSeenAt = 1000))
            val demo = dao.addTrip(TripEntity(driverId = a, source = "DEMO", startedAt = 1000, lastSeenAt = 1000))
            dao.record(EventEntity(driverId = a, tripId = real, source = "PLEOS", scenarioId = "G01", category = "distance", tone = "GOOD", reason = "test observation", confidence = .9, observedAt = 2000))
            dao.record(EventEntity(driverId = a, tripId = demo, source = "DEMO", scenarioId = "B01", category = "distance", tone = "CAUTION", reason = "test simulation", confidence = 1.0, observedAt = 2000))
            db.close(); db = Room.databaseBuilder(context, GyeotDatabase::class.java, name).build()
            assertEquals(1, db.dao().habitSummary(a, "PLEOS").single().good)
            assertEquals(0, db.dao().habitSummary(a, "PLEOS").single().caution)
            assertEquals(1, db.dao().habitSummary(a, "DEMO").single().caution)
            assertTrue(db.dao().habitSummary(b, "PLEOS").isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun eventCannotBeWrittenToAnotherDriversTrip() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, GyeotDatabase::class.java).build()
        try {
            val a = db.dao().addDriver(DriverEntity(name = "A")); val b = db.dao().addDriver(DriverEntity(name = "B"))
            val trip = db.dao().addTrip(TripEntity(driverId = a, source = "PLEOS", startedAt = 1000, lastSeenAt = 1000))
            val result = runCatching { db.dao().record(EventEntity(driverId = b, tripId = trip, source = "PLEOS", scenarioId = "G01", category = "distance", tone = "GOOD", reason = "test", confidence = 1.0, observedAt = 2000)) }
            assertTrue(result.isFailure)
            assertTrue(db.dao().habitSummary(b, "PLEOS").isEmpty())
        } finally { db.close() }
    }
    @Test fun interruptedTripEndsAtLastHeartbeatRatherThanNextBoot() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, GyeotDatabase::class.java).build()
        try {
            val driver = db.dao().addDriver(DriverEntity(name = "A"))
            val trip = db.dao().addTrip(TripEntity(driverId = driver, source = "PLEOS", startedAt = 1000, lastSeenAt = 3000))
            db.dao().recoverTrips()
            assertEquals(3000L, db.dao().trip(trip)?.endedAt)
            assertEquals(true, db.dao().trip(trip)?.interrupted)
        } finally { db.close() }
    }
}
