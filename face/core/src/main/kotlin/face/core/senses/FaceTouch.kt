package face.core.senses

public enum class FacePart { LeftEye, RightEye, Cheek, Mouth, Forehead, Face, Outside }

public enum class TouchGesture { Tap, LongPressStart, LongPressEnd, DragStart, Drag, DragEnd }

// lookX/lookY: where the touch is relative to the face centre, about -1..1 across the face (positive is right and up).
public class FaceTouch(
    public val gesture: TouchGesture,
    public val part: FacePart,
    public val lookX: Float,
    public val lookY: Float,
)
