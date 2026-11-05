package com.example.roomkit.Date.Local.Dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.roomkit.Date.Local.Entity.BillingPeriodEntity
import com.example.roomkit.Date.Local.Entity.BillingPeriodEventEntity

@Dao
interface BillingPeriodDao {
    @Query("SELECT * FROM billing_periods WHERE billingYear=:year AND billingMonth=:month")
    suspend fun get(year: Int, month: Int): BillingPeriodEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(value: BillingPeriodEntity)
    @Update suspend fun update(value: BillingPeriodEntity)
    @Insert suspend fun event(value: BillingPeriodEventEntity): Long
    @Query("SELECT * FROM billing_period_events WHERE billingYear=:year AND billingMonth=:month ORDER BY eventId")
    suspend fun history(year: Int, month: Int): List<BillingPeriodEventEntity>
}
