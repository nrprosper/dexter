package face.render

import face.core.senses.FacePart

// What a touch landed on, plus where it is relative to the face centre (about -1..1, positive right and up).
public class FaceHit(public val part: FacePart, public val lookX: Float, public val lookY: Float)
