package com.example.roomkit.Date.Ui.Tenant

enum class MatchReason {
    SAME_PHONE,
    SAME_ID,
    SAME_NAME_AND_PHONE,
    SAME_NAME_PHONE_AND_ID
}

data class TenantDuplicateMatch(
    val tenantId: Long,
    val fullName: String,
    val phoneNumber: String,
    val idOrPassport: String?,
    val matchReason: MatchReason
)