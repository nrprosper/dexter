package face.core.personality

import face.api.Personality

public object PersonalityPresets {
    public val ids: List<String> = listOf("dexter", "sage")

    public fun load(id: String): Personality = PersonalityCodec.decode(text(id))

    public fun text(id: String): String {
        val path = "/face/personality/$id.json"
        return PersonalityPresets::class.java.getResourceAsStream(path)?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalArgumentException("no bundled personality '$id'")
    }
}
