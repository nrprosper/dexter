package face.core.senses

import face.core.anim.Layer
import face.core.rig.FacePose

// After speech and before reactions: phone conditions, the volume visit, and touch.
internal class SensesLayer(
    private val device: DeviceResponder,
    private val volume: VolumeWatch,
    private val touch: TouchResponder,
) : Layer() {
    override fun apply(target: FacePose) {
        device.apply(target)
        volume.apply(target)
        touch.apply(target)
    }
}
