package voice.kokoro

// The English speakers in Kokoro v1.0's voices.bin, by speaker id (sherpa-onnx's generate_voices_bin.py order).
// a = American, b = British; f = female, m = male. British voices read with the UK lexicon.
public class KokoroSpeaker(public val sid: Int, public val code: String) {
    public val british: Boolean get() = code.startsWith("b")
    public val female: Boolean get() = code[1] == 'f'
    public val id: String get() = PREFIX + code
    public val label: String
        get() = code.substringAfter('_').replaceFirstChar { it.uppercase() } +
            " · " + (if (british) "British" else "American") + " " + (if (female) "female" else "male")

    public companion object {
        public const val PREFIX: String = "kokoro:"
    }
}

public object KokoroVoices {
    public val english: List<KokoroSpeaker> = listOf(
        "af_alloy", "af_aoede", "af_bella", "af_heart", "af_jessica", "af_kore", "af_nicole", "af_nova", "af_river",
        "af_sarah", "af_sky", "am_adam", "am_echo", "am_eric", "am_fenrir", "am_liam", "am_michael", "am_onyx", "am_puck",
        "am_santa", "bf_alice", "bf_emma", "bf_isabella", "bf_lily", "bm_daniel", "bm_fable", "bm_george", "bm_lewis",
    ).mapIndexed { sid, code -> KokoroSpeaker(sid, code) }

    // A warm, clear default that suits a playful companion.
    public val default: KokoroSpeaker = english.first { it.code == "af_heart" }

    public fun byId(id: String?): KokoroSpeaker? = english.firstOrNull { it.id == id }
}
