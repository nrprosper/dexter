package face.api

// Where the character goes on screen, as a stage direction. Placements (Center to Away) hold until the next move and
// drift home after speech; Hop, LeanIn and Peek are one-shot beats. The engine decides the actual motion.
public enum class StageMove { Center, Left, Right, Up, Down, Closer, Away, Hop, LeanIn, Peek }
