@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Factory

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.roomkit.Date.Repositories.BillingRepository
import com.example.roomkit.Date.Repositories.DashboardRepository
import com.example.roomkit.Date.Repositories.MeterReadingRepository
import com.example.roomkit.Date.Repositories.RoomDetailRepository
import com.example.roomkit.Date.Repositories.TenancyRepository
import com.example.roomkit.Date.Repositories.TenantRepository
import com.example.roomkit.Date.Ui.Billing.BillingViewModel
import com.example.roomkit.Date.Ui.Billing.InvoiceDetailViewModel
import com.example.roomkit.Date.Ui.Billing.InvoicePreviewViewModel
import com.example.roomkit.Date.Ui.Billing.PaymentQrViewModel
import com.example.roomkit.Date.Ui.Dashboard.DashboardViewModel
import com.example.roomkit.Date.Ui.Meter.MeterReadingViewModel
import com.example.roomkit.Date.Ui.Payment.PaymentsViewModel
import com.example.roomkit.Date.Ui.Room.AddRoomViewModel
import com.example.roomkit.Date.Ui.Room.EditRoomViewModel
import com.example.roomkit.Date.Ui.Room.RoomDetailViewModel
import com.example.roomkit.Date.Ui.Room.RoomsViewModel
import com.example.roomkit.Date.Ui.Tenancy.MoveInViewModel
import com.example.roomkit.Date.Ui.Tenant.TenantDetailsViewModel
import com.example.roomkit.Date.Ui.Tenant.TenantsViewModel
import com.roomkit.data.local.AppDatabase
import com.roomkit.data.repository.BuildingRepository
import com.roomkit.data.repository.RoomRepository
import com.roomkit.ui.buildings.BuildingsViewModel

class ViewModelFactory(
    private val context: Context,
    private val targetId: Long? = null,
    private val secondTargetId: Long? = null
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = AppDatabase.getInstance(context)
        val buildingRepo = BuildingRepository(db.buildingDao())
        val roomRepo = RoomRepository(db.roomDao())
        val tenantRepo = TenantRepository(db.tenantDao())

        val tenancyRepo = TenancyRepository(
            db = db,
            tenancyDao = db.tenancyDao(),
            roomDao = db.roomDao(),
            tenantDao = db.tenantDao(),
            buildingDao = db.buildingDao(),
            meterReadingDao = db.meterReadingDao()
        )

        // Single shared repository instance for all billing-related ViewModels
        val billingRepo = BillingRepository(
            context = context.applicationContext,
            db = db,
            buildingDao = db.buildingDao(),
            invoiceDao = db.invoiceDao(),
            paymentDao = db.paymentDao(),
            tenancyDao = db.tenancyDao(),
            tenantDao = db.tenantDao(),
            roomDao = db.roomDao(),
            meterReadingDao = db.meterReadingDao()
        )

        return when {
            modelClass.isAssignableFrom(BuildingsViewModel::class.java) -> {
                BuildingsViewModel(buildingRepo) as T
            }
            modelClass.isAssignableFrom(RoomsViewModel::class.java) -> {
                requireNotNull(targetId) { "buildingId is required for RoomsViewModel" }
                RoomsViewModel(targetId, buildingRepo, roomRepo) as T
            }
            modelClass.isAssignableFrom(TenantsViewModel::class.java) -> {
                TenantsViewModel(tenantRepo) as T
            }
            modelClass.isAssignableFrom(PaymentsViewModel::class.java) -> {
                PaymentsViewModel(billingRepo) as T
            }
            modelClass.isAssignableFrom(BillingViewModel::class.java) -> {
                BillingViewModel(billingRepo) as T
            }
            modelClass.isAssignableFrom(DashboardViewModel::class.java) -> {
                val dashboardRepo = DashboardRepository(
                    db.buildingDao(),
                    db.roomDao(),
                    db.tenancyDao(),
                    db.invoiceDao(),
                    db.paymentDao()
                )
                DashboardViewModel(dashboardRepo) as T
            }
            modelClass.isAssignableFrom(PaymentQrViewModel::class.java) -> {
                val validInvoiceId = targetId ?: 0L
                PaymentQrViewModel(validInvoiceId, billingRepo) as T
            }
            modelClass.isAssignableFrom(EditRoomViewModel::class.java) -> {
                requireNotNull(targetId) { "roomId is required for EditRoomViewModel" }
                EditRoomViewModel(
                    roomId = targetId,
                    roomRepository = roomRepo,
                    buildingRepository = buildingRepo,
                    tenancyDao = db.tenancyDao()
                ) as T
            }
            modelClass.isAssignableFrom(TenantDetailsViewModel::class.java) -> {
                requireNotNull(targetId) { "tenantId is required for TenantDetailsViewModel" }
                TenantDetailsViewModel(targetId, tenantRepo, tenancyRepo) as T
            }
            modelClass.isAssignableFrom(MoveInViewModel::class.java) -> {
                MoveInViewModel(targetId ?: 0L, secondTargetId ?: 0L, tenancyRepo) as T
            }
            modelClass.isAssignableFrom(InvoicePreviewViewModel::class.java) -> {
                requireNotNull(targetId) { "roomId is required for InvoicePreviewViewModel" }
                InvoicePreviewViewModel(targetId, billingRepo) as T
            }
            modelClass.isAssignableFrom(AddRoomViewModel::class.java) -> {
                requireNotNull(targetId) { "buildingId is required for AddRoomViewModel" }
                AddRoomViewModel(targetId, buildingRepo, roomRepo) as T
            }
            modelClass.isAssignableFrom(MeterReadingViewModel::class.java) -> {
                requireNotNull(targetId) { "roomId is required for MeterReadingViewModel" }
                val meterRepo = MeterReadingRepository(
                    db,
                    db.roomDao(),
                    db.tenancyDao(),
                    db.tenantDao(),
                    db.meterReadingDao(),
                    db.invoiceDao()
                )
                MeterReadingViewModel(targetId, meterRepo) as T
            }
            modelClass.isAssignableFrom(InvoiceDetailViewModel::class.java) -> {
                requireNotNull(targetId) { "invoiceId is required for InvoiceDetailViewModel" }
                InvoiceDetailViewModel(targetId, billingRepo) as T
            }
            modelClass.isAssignableFrom(RoomDetailViewModel::class.java) -> {
                requireNotNull(targetId) { "roomId is required for RoomDetailViewModel" }
                val detailRepo = RoomDetailRepository(
                    db,
                    db.roomDao(),
                    db.buildingDao(),
                    db.tenancyDao(),
                    db.tenantDao(),
                    db.meterReadingDao(),
                    db.invoiceDao()
                )
                RoomDetailViewModel(targetId, detailRepo, tenancyRepo) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}