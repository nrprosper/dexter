package face.speech

// How PCM becomes the speech envelope. Loudness maps floorDb..0 dBFS to 0..1; brightness is the share of energy above splitHz.
public data class EnvelopeConfig(
    val hopMillis: Int = 10,
    val floorDb: Float = -60f,
    val splitHz: Float = 1_000f,
    val brightnessLow: Float = 0.08f,
    val brightnessHigh: Float = 0.6f,
    val emphasisDb: Float = 3f,
    val emphasisRangeDb: Float = 9f,
    val averageMillis: Int = 300,
    val voicedDb: Float = -45f,
    val capacitySeconds: Int = 30,
) {
    init {
        require(hopMillis in 5..50) { "hopMillis must be in 5..50" }
        require(floorDb < 0f) { "floorDb must be negative" }
        require(brightnessHigh > brightnessLow) { "brightnessHigh must exceed brightnessLow" }
        require(capacitySeconds >= 2) { "capacitySeconds must be at least 2" }
    }
}
