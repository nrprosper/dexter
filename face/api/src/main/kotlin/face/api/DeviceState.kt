package face.api

// Sustained phone conditions the face shows: latest wins, like the other state setters.
public data class DeviceState(
    val batteryLow: Boolean = false,
    val charging: Boolean = false,
    val faceDown: Boolean = false,
    val heat: Heat = Heat.Normal,
    val handOver: Boolean = false,
    val inPocket: Boolean = false,
    val dark: Boolean = false,
    val batterySaver: Boolean = false,
    val dayPart: DayPart = DayPart.Day,
)

public enum class Heat { Normal, Warm, Hot, Critical }

public enum class DayPart { Morning, Day, Evening, Night }
