package com.example.roomkit.Date.Local.Dao

import androidx.room.*
import com.example.roomkit.Date.Local.Entity.MeterReadingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MeterReadingDao {
    @Query("SELECT * FROM meter_readings WHERE roomId=:roomId ORDER BY readingDate DESC, readingId DESC")
    suspend fun historyForRoom(roomId: Long): List<MeterReadingEntity>

    @Query("SELECT COUNT(*) FROM meter_readings WHERE readingType='MONTHLY' AND roomId=:roomId AND tenancyId=0")
    suspend fun unassignedCount(roomId: Long): Int

    @Query("SELECT * FROM meter_readings WHERE tenancyId=:id ORDER BY readingId")
    suspend fun getForTenancy(id: Long): List<MeterReadingEntity>


    @Query("""
        SELECT * FROM meter_readings
        WHERE readingType='MONTHLY' AND roomId = :roomId AND tenancyId = COALESCE((SELECT tenancyId FROM tenancies WHERE roomId = :roomId AND status = 'ACTIVE' LIMIT 1), -1) AND meterType = :meterType
        ORDER BY billingYear DESC, billingMonth DESC, readingId DESC
        LIMIT 1
    """)
    fun observeLatestReading(roomId: Long, meterType: String): Flow<MeterReadingEntity?>

    @Query("""
        SELECT * FROM meter_readings
        WHERE readingType='MONTHLY' AND roomId = :roomId AND tenancyId = COALESCE((SELECT tenancyId FROM tenancies WHERE roomId = :roomId AND status = 'ACTIVE' LIMIT 1), -1) AND meterType = :meterType AND billingMonth = :month AND billingYear = :year
        LIMIT 1
    """)
    suspend fun getReadingForPeriod(roomId: Long, meterType: String, month: Int, year: Int): MeterReadingEntity?

    @Query("""
        SELECT * FROM meter_readings
        WHERE readingType='MONTHLY' AND roomId = :roomId AND tenancyId = COALESCE((SELECT tenancyId FROM tenancies WHERE roomId = :roomId AND status = 'ACTIVE' LIMIT 1), -1) AND meterType = :meterType
        ORDER BY billingYear DESC, billingMonth DESC, readingId DESC
        LIMIT 1
    """)
    suspend fun getLatestReading(roomId: Long, meterType: String): MeterReadingEntity?

    @Query("""
        SELECT * FROM meter_readings
        WHERE readingType='MONTHLY' AND roomId = :roomId AND tenancyId = COALESCE((SELECT tenancyId FROM tenancies WHERE roomId = :roomId AND status = 'ACTIVE' LIMIT 1), -1)
          AND meterType = :meterType
          AND billingMonth = :month
          AND billingYear = :year
        LIMIT 1
    """)
    suspend fun getReadingForMonth(roomId: Long, meterType: String, month: Int, year: Int): MeterReadingEntity?
    @Query("""SELECT * FROM meter_readings WHERE readingType='MONTHLY' AND roomId=:roomId AND meterType=:meterType
        AND tenancyId=(SELECT tenancyId FROM tenancies WHERE roomId=:roomId AND status='ACTIVE' LIMIT 1)
        AND (billingYear < :year OR (billingYear=:year AND billingMonth < :month))
        ORDER BY billingYear DESC,billingMonth DESC LIMIT 1""")
    suspend fun getLatestReadingBefore(roomId: Long, meterType: String, month: Int, year: Int): MeterReadingEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOrUpdateReading(reading: MeterReadingEntity): Long
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReading(reading: MeterReadingEntity): Long
    @Update
    suspend fun updateReading(reading: MeterReadingEntity)

    @Query("""
    SELECT * FROM meter_readings
    WHERE readingType='MONTHLY' AND roomId = :roomId AND tenancyId = COALESCE((SELECT tenancyId FROM tenancies WHERE roomId = :roomId AND status = 'ACTIVE' LIMIT 1), -1)
      AND meterType = :meterType
      AND billingMonth = :month
      AND billingYear = :year
    LIMIT 1
""")
    fun observeReadingForPeriod(
        roomId: Long,
        meterType: String,
        month: Int,
        year: Int
    ): Flow<MeterReadingEntity?>

    @Query("""SELECT * FROM meter_readings WHERE readingType='MONTHLY' AND roomId = :roomId
        AND tenancyId = (SELECT tenancyId FROM tenancies WHERE roomId = :roomId AND status = 'ACTIVE' LIMIT 1)
        AND meterType = :meterType AND (billingYear < :year OR (billingYear = :year AND billingMonth < :month))
        ORDER BY billingYear DESC, billingMonth DESC, readingId DESC LIMIT 1""")
    suspend fun getReadingBeforePeriod(
        roomId: Long,
        meterType: String,
        month: Int,
        year: Int
    ): MeterReadingEntity?

    @Query("""
    SELECT EXISTS(
        SELECT 1 FROM meter_readings
        WHERE readingType='MONTHLY' AND roomId = :roomId AND tenancyId = COALESCE((SELECT tenancyId FROM tenancies WHERE roomId = :roomId AND status = 'ACTIVE' LIMIT 1), -1)
          AND meterType = :meterType
          AND (
              billingYear > :year
              OR (billingYear = :year AND billingMonth > :month)
          )
    )
""")
    suspend fun hasReadingAfterPeriod(
        roomId: Long,
        meterType: String,
        month: Int,
        year: Int
    ): Boolean
}
