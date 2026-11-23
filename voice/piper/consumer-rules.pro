# sherpa-onnx's native code reads its config classes' fields and calls methods by name over JNI.
-keep class com.k2fsa.sherpa.onnx.** { *; }
# Called from native code as invoke(float[]): Integer.
-keep class voice.piper.SherpaSynth$StopCheck { public java.lang.Integer invoke(float[]); }
