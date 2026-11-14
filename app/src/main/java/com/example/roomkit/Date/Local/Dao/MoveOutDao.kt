package com.example.roomkit.Date.Local.Dao
import androidx.room.*
import com.example.roomkit.Date.Local.Entity.MoveOutSettlementEntity
@Dao
interface MoveOutDao {
    @Query("SELECT * FROM move_out_settlements WHERE tenancyId = :id")
    suspend fun get(id: Long): MoveOutSettlementEntity?
    @Query("SELECT * FROM move_out_settlements WHERE roomId = :id ORDER BY moveOutDate DESC LIMIT 1")
    suspend fun latestForRoom(id: Long): MoveOutSettlementEntity?
    @Query("UPDATE move_out_settlements SET refundPaid=1, refundPaidAt=:paidAt, refundReference=:reference WHERE tenancyId=:id AND refundPaid=0 AND refundKhr>0")
    suspend fun markRefundPaid(id: Long, paidAt: Long, reference: String?): Int
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(value: MoveOutSettlementEntity)
}
