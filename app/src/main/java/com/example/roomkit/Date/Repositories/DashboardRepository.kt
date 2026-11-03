package com.example.roomkit.Date.Repositories


import com.example.roomkit.Date.Local.Dao.InvoiceDao
import com.example.roomkit.Date.Local.Dao.PaymentDao
import com.example.roomkit.Date.Local.Entity.BuildingEntity
import com.example.roomkit.Date.Local.Entity.InvoiceEntity
import com.example.roomkit.Date.Local.Entity.PaymentEntity
import com.example.roomkit.Date.Local.Entity.RoomEntity
import com.example.roomkit.Date.Local.Entity.TenancyEntity
import com.example.roomkit.data.local.dao.RoomDao
import com.example.roomkit.data.local.dao.TenancyDao
import com.roomkit.data.local.dao.BuildingDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class DashboardRawData(
    val buildings: List<BuildingEntity>,
    val rooms: List<RoomEntity>,
    val tenancies: List<TenancyEntity>,
    val invoices: List<InvoiceEntity>,
    val payments: List<PaymentEntity>
)

class DashboardRepository(
    private val buildingDao: BuildingDao,
    private val roomDao: RoomDao,
    private val tenancyDao: TenancyDao,
    private val invoiceDao: InvoiceDao,
    private val paymentDao: PaymentDao
) {
    fun observeDashboardData(): Flow<DashboardRawData> {
        return combine(
            buildingDao.observeAllBuildings(),
            roomDao.observeAllRooms(),
            tenancyDao.observeAllTenancies(),
            invoiceDao.observeAllInvoices(),
            paymentDao.observeAllPayments()
        ) { buildings, rooms, tenancies, invoices, payments ->
            DashboardRawData(buildings, rooms, tenancies, invoices, payments)
        }
    }
}