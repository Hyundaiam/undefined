package io.hyundaiam.gyeot

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "drivers")
data class DriverEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val persona: String = "friend", val createdAt: Long = System.currentTimeMillis(), val seatBaseline: String = "{}", val closedWindows: String = "{}")
@Entity(tableName = "trips", indices = [Index("driverId")])
data class TripEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val driverId: Long, val source: String, val startedAt: Long, val lastSeenAt: Long, val endedAt: Long? = null, val interrupted: Boolean = false)
@Entity(tableName = "events", indices = [Index("driverId"), Index("tripId")])
data class EventEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val driverId: Long, val tripId: Long, val source: String, val scenarioId: String, val category: String, val tone: String, val reason: String, val confidence: Double, val observedAt: Long, val engine: String = "LOCAL_RULE_CANDIDATE")
@Entity(tableName = "habits", primaryKeys = ["driverId", "source", "category"])
data class HabitEntity(val driverId: Long, val source: String, val category: String, val good: Int = 0, val caution: Int = 0, val updatedAt: Long)
@Entity(tableName = "observations", indices = [Index("driverId")])
data class ObservationEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val driverId: Long, val tripId: Long, val source: String, val observedAt: Long, val payload: String)
@Entity(tableName = "voice_clips", primaryKeys = ["persona", "scenarioId"])
data class ClipEntity(val persona: String, val scenarioId: String, val fileName: String, val durationMs: Long, val updatedAt: Long)
data class TripStats(val id: Long, val startedAt: Long, val endedAt: Long?, val interrupted: Boolean, val good: Int, val caution: Int)

@Dao
abstract class GyeotDao {
    @Query("SELECT * FROM drivers ORDER BY id") abstract fun drivers(): Flow<List<DriverEntity>>
    @Query("SELECT * FROM drivers ORDER BY id") abstract suspend fun allDrivers(): List<DriverEntity>
    @Query("SELECT * FROM drivers WHERE id = :id") abstract suspend fun driver(id: Long): DriverEntity?
    @Insert abstract suspend fun addDriver(driver: DriverEntity): Long
    @Query("UPDATE drivers SET persona = :persona WHERE id = :id") abstract suspend fun setPersona(id: Long, persona: String)
    @Query("UPDATE drivers SET seatBaseline = :seats, closedWindows = :windows WHERE id = :id") abstract suspend fun calibrate(id: Long, seats: String, windows: String)
    @Insert abstract suspend fun addTrip(trip: TripEntity): Long
    @Query("UPDATE trips SET lastSeenAt = :time WHERE id = :id AND endedAt IS NULL") abstract suspend fun heartbeat(id: Long, time: Long)
    @Query("UPDATE trips SET endedAt = :time, lastSeenAt = :time WHERE id = :id AND endedAt IS NULL") abstract suspend fun finishTrip(id: Long, time: Long)
    @Query("UPDATE trips SET endedAt = lastSeenAt, interrupted = 1 WHERE endedAt IS NULL") abstract suspend fun recoverTrips()
    @Query("SELECT * FROM trips WHERE driverId = :driverId AND source = :source ORDER BY startedAt DESC LIMIT 30") abstract fun trips(driverId: Long, source: String): Flow<List<TripEntity>>
    @Query("SELECT * FROM trips WHERE id = :id") abstract suspend fun trip(id: Long): TripEntity?
    @Query("SELECT t.id, t.startedAt, t.endedAt, t.interrupted, COALESCE(SUM(CASE WHEN e.tone = 'GOOD' THEN 1 ELSE 0 END), 0) AS good, COALESCE(SUM(CASE WHEN e.tone = 'CAUTION' THEN 1 ELSE 0 END), 0) AS caution FROM trips t LEFT JOIN events e ON e.tripId = t.id WHERE t.driverId = :driverId AND t.source = :source GROUP BY t.id ORDER BY t.startedAt DESC LIMIT 30") abstract fun tripStats(driverId: Long, source: String): Flow<List<TripStats>>
    @Query("SELECT * FROM events WHERE driverId = :driverId AND source = :source ORDER BY observedAt DESC, id DESC LIMIT 100") abstract fun events(driverId: Long, source: String): Flow<List<EventEntity>>
    @Query("SELECT * FROM events WHERE driverId = :driverId AND source = :source ORDER BY observedAt DESC, id DESC LIMIT 10") abstract suspend fun recentEvents(driverId: Long, source: String): List<EventEntity>
    @Query("SELECT * FROM habits WHERE driverId = :driverId AND source = :source ORDER BY category") abstract fun habits(driverId: Long, source: String): Flow<List<HabitEntity>>
    @Query("SELECT * FROM habits WHERE driverId = :driverId AND source = :source ORDER BY category") abstract suspend fun habitSummary(driverId: Long, source: String): List<HabitEntity>
    @Query("SELECT * FROM habits WHERE driverId = :driverId AND source = :source AND category = :category") abstract suspend fun habit(driverId: Long, source: String, category: String): HabitEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun putHabit(habit: HabitEntity)
    @Insert abstract suspend fun addEvent(event: EventEntity): Long
    @Insert abstract suspend fun addObservation(observation: ObservationEntity)
    @Query("DELETE FROM observations WHERE id NOT IN (SELECT id FROM observations ORDER BY id DESC LIMIT 5000)") abstract suspend fun trimObservations()
    @Query("SELECT * FROM voice_clips") abstract fun clips(): Flow<List<ClipEntity>>
    @Query("SELECT * FROM voice_clips WHERE persona = :persona AND scenarioId = :scenarioId") abstract suspend fun clip(persona: String, scenarioId: String): ClipEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun putClip(clip: ClipEntity)
    @Query("DELETE FROM voice_clips WHERE persona = :persona AND scenarioId = :scenarioId") abstract suspend fun removeClip(persona: String, scenarioId: String)

    @Transaction
    open suspend fun record(event: EventEntity): Long {
        val trip = requireNotNull(trip(event.tripId))
        require(trip.endedAt == null && trip.driverId == event.driverId && trip.source == event.source)
        val id = addEvent(event)
        val previous = habit(event.driverId, event.source, event.category)
            ?: HabitEntity(event.driverId, event.source, event.category, updatedAt = event.observedAt)
        putHabit(previous.copy(good = previous.good + if (event.tone == "GOOD") 1 else 0, caution = previous.caution + if (event.tone == "CAUTION") 1 else 0, updatedAt = event.observedAt))
        heartbeat(trip.id, event.observedAt)
        return id
    }
}

@Database(entities = [DriverEntity::class, TripEntity::class, EventEntity::class, HabitEntity::class, ObservationEntity::class, ClipEntity::class], version = 1, exportSchema = false)
abstract class GyeotDatabase : RoomDatabase() { abstract fun dao(): GyeotDao }
