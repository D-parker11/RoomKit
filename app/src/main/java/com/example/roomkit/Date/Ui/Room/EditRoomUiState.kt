package com.example.roomkit.Date.Ui.Room

data class EditRoomUiState(
    val roomId: Long = 0L,
    val buildingId: Long = 0L,
    val buildingName: String = "",
    val originalRoomNumber: String = "",
    val roomNumber: String = "",
    val floor: String = "",
    val roomType: String = "Studio",
    val notes: String = "",
    val defaultRentKhrInput: String = "",
    val originalRentKhrInput: String = "",
    val isOccupied: Boolean = false,
    val isArchived: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val isSuccess: Boolean = false,
    val isDeleted: Boolean = false,
    val errorMessage: String? = null
) {
    val isSaveEnabled: Boolean
        get() = roomNumber.isNotBlank() &&
                defaultRentKhrInput.isNotBlank() &&
                !isSaving &&
                (roomNumber != originalRoomNumber ||
                        defaultRentKhrInput != originalRentKhrInput ||
                        floor.isNotBlank() ||
                        notes.isNotBlank())
}