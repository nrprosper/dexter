package face.core.anim

import face.core.rig.FacePose

// A layer adds its opinion to the target pose; the stack applies layers bottom to top in a fixed order.
internal abstract class Layer {
    var enabled = true

    abstract fun apply(target: FacePose)
}

internal class LayerStack(private val layers: Array<Layer>) {
    fun apply(target: FacePose) {
        for (i in layers.indices) {
            val layer = layers[i]
            if (layer.enabled) layer.apply(target)
        }
    }
}
