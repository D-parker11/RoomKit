
package com.example.roomkit.Date.Ui.MoveOut

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomkit.Date.Repositories.*
import com.example.roomkit.Date.Local.Entity.MoveOutSettlementEntity
import com.roomkit.data.local.AppDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MoveOutState(
    val tenantName: String = "", val buildingName: String = "",
    val loading: Boolean = true, val saving: Boolean = false,
    val snapshot: MoveOutSnapshot? = null, val review: MoveOutReview? = null,
    val settlement: MoveOutSettlementEntity? = null,
    val error: String? = null, val success: Boolean = false
)
class MoveOutViewModel(private val id: Long, private val db: AppDatabase) : ViewModel() {
    private val repo=MoveOutRepository(db)
    private val mutable=MutableStateFlow(MoveOutState())
    val state=mutable.asStateFlow()
    var selectedDate: Long=System.currentTimeMillis()
    init { load() }
    fun load() { viewModelScope.launch {
        mutable.update { it.copy(loading=true,error=null,review=null) }
        try {
            val saved=db.moveOutDao().get(id)
            val snapshot=repo.snapshot(id)
            mutable.value=MoveOutState(tenantName=db.tenantDao().getTenantById(snapshot.tenancy.tenantId)?.fullName.orEmpty(), buildingName=db.buildingDao().getBuildingById(snapshot.room.buildingId)?.name.orEmpty(), loading=false,snapshot=snapshot,settlement=saved)
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) { mutable.update { it.copy(loading=false,error=e.message) } }
    } }
    fun invalidateReview() { if(!state.value.saving) mutable.update { it.copy(review=null,error=null) } }
    fun review(input: MoveOutInput) { viewModelScope.launch {
        try {
            val snapshot=repo.snapshot(id)
            mutable.update { it.copy(snapshot=snapshot,review=repo.review(snapshot,input),error=null) }
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) { mutable.update { it.copy(review=null,error=e.message) } }
    } }
    fun recordRefundPaid(paidAt: Long, reference: String?) {
        if(state.value.saving) return
        mutable.update { it.copy(saving=true) }
        viewModelScope.launch {
            try {
                check(db.moveOutDao().markRefundPaid(id, paidAt, reference?.trim()?.takeIf(String::isNotEmpty))==1) { "Refund was already recorded or no refund is due." }
                mutable.update { it.copy(saving=false,settlement=db.moveOutDao().get(id)) }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(saving=false,error=e.message) } }
        }
    }
    fun complete() {
        val r=state.value.review ?: return
        if(state.value.saving || state.value.success) return
        mutable.update { it.copy(saving=true,error=null) }
        viewModelScope.launch {
            try {
                repo.complete(r)
                mutable.update { it.copy(saving=false,success=true,snapshot=repo.snapshot(id),settlement=db.moveOutDao().get(id)) }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(saving=false,review=null,error=e.message) } }
        }
    }
}

