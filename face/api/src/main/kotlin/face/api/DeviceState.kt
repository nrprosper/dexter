package face.api

// Sustained phone conditions the face shows: latest wins, like the other state setters.
public data class DeviceState(
    val batteryLow: Boolean = false,
    val charging: Boolean = false,
    val faceDown: Boolean = false,
)
