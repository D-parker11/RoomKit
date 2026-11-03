package com.example.roomkit.Date.Local.Entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "move_out_settlements", foreignKeys = [ForeignKey(entity = TenancyEntity::class, parentColumns = ["tenancyId"], childColumns = ["tenancyId"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["tenancyId"], unique = true)])
data class MoveOutSettlementEntity(
    @PrimaryKey val tenancyId: Long,
    val roomId: Long,
    val moveOutDate: Long,
    val electricityFinal: Long,
    val waterFinal: Long?,
    val depositHeldKhr: Long,
    val appliedKhr: Long,
    val deductionsKhr: Long,
    val deductionReason: String,
    val refundKhr: Long,
    val refundPaid: Boolean,
    val debtKhr: Long,
    val finalInvoiceId: Long?,
    val createdAt: Long
)
