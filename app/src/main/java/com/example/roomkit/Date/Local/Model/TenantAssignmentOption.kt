package com.example.roomkit.Date.Local.Model

import java.util.Locale

/** Query projection only. This is not a Room entity or an extra database table. */
data class TenantAssignmentOption(
    val tenantId: Long,
    val fullName: String,
    val phoneNumber: String,
    val activeTenancyId: Long?,
    val currentRoomNumber: String?,
    val currentBuildingName: String?
) {
    val isAvailable: Boolean get() = activeTenancyId == null

    val assignmentLabel: String get() = if (isAvailable) {
        "Available · No active room"
    } else {
        val room = currentRoomNumber?.takeIf { it.isNotBlank() }
            ?.let { "Room $it" } ?: "Active tenancy"
        listOfNotNull("Assigned", room, currentBuildingName?.takeIf { it.isNotBlank() })
            .joinToString(" · ")
    }

    fun matches(query: String): Boolean {
        val normalized = query.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return true
        val searchable = "$fullName $phoneNumber".lowercase(Locale.ROOT)
        val words = normalized.split(Regex("\\s+"))
        if (words.all { searchable.contains(it) }) return true
        // A formatted phone number can be found without spaces or punctuation.
        val digits = normalized.filter { it.isDigit() }
        return digits.isNotEmpty() && normalized.all { it.isDigit() || it in "+-() ." } &&
                phoneNumber.filter { it.isDigit() }.contains(digits)
    }
}

enum class TenantAssignmentFilter { AVAILABLE, ALL, ASSIGNED }

fun filterTenantAssignmentOptions(
    options: List<TenantAssignmentOption>,
    query: String,
    filter: TenantAssignmentFilter
): List<TenantAssignmentOption> = options.asSequence()
    .filter { when (filter) {
        TenantAssignmentFilter.AVAILABLE -> it.isAvailable
        TenantAssignmentFilter.ASSIGNED -> !it.isAvailable
        TenantAssignmentFilter.ALL -> true
    } }
    .filter { it.matches(query) }
    .sortedWith(compareBy<TenantAssignmentOption> { !it.isAvailable }
        .thenBy { it.fullName.lowercase(Locale.ROOT) }.thenBy { it.tenantId })
    .toList()
