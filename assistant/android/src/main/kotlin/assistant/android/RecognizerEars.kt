package assistant.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import assistant.core.Ears
import assistant.core.HearingEvent
import assistant.core.HearingFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

// One recognition per listen() with Android's SpeechRecognizer: the on-device recognizer when the phone has one
// (falling back to the default one if its language pack is missing), partial results on, offline preferred.
public class RecognizerEars(context: Context) : Ears {
    private val app = context.applicationContext

    @Volatile
    private var onDeviceBroken = false

    override fun listen(): Flow<HearingEvent> = callbackFlow {
        if (app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            trySend(HearingEvent.Failed(HearingFailure.NoPermission))
            close()
            awaitClose()
            return@callbackFlow
        }
        val onDevice = !onDeviceBroken && SpeechRecognizer.isOnDeviceRecognitionAvailable(app)
        if (!onDevice && !SpeechRecognizer.isRecognitionAvailable(app)) {
            trySend(HearingEvent.Failed(HearingFailure.Unavailable))
            close()
            awaitClose()
            return@callbackFlow
        }
        var recognizer = create(onDevice)
        var usingOnDevice = onDevice
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(HearingEvent.Ready)
            }

            override fun onRmsChanged(rmsdB: Float) {
                trySend(HearingEvent.Level(((rmsdB - RMS_FLOOR) / (RMS_CEILING - RMS_FLOOR)).coerceIn(0f, 1f)))
            }

            override fun onPartialResults(partialResults: Bundle?) {
                best(partialResults)?.let { trySend(HearingEvent.Partial(it)) }
            }

            override fun onResults(results: Bundle?) {
                val text = best(results)
                trySend(if (text.isNullOrBlank()) HearingEvent.NothingHeard else HearingEvent.Heard(text))
                close()
            }

            override fun onError(error: Int) {
                if (usingOnDevice && (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                        error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED)
                ) {
                    onDeviceBroken = true
                    usingOnDevice = false
                    recognizer.destroy()
                    recognizer = create(onDevice = false)
                    recognizer.setRecognitionListener(this)
                    recognizer.startListening(intent)
                    return
                }
                trySend(event(error))
                close()
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }
        recognizer.setRecognitionListener(listener)
        recognizer.startListening(intent)
        awaitClose {
            recognizer.cancel()
            recognizer.destroy()
        }
    }.flowOn(Dispatchers.Main)

    private fun create(onDevice: Boolean): SpeechRecognizer =
        if (onDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(app) else SpeechRecognizer.createSpeechRecognizer(app)

    private fun best(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()

    private fun event(error: Int): HearingEvent = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> HearingEvent.NothingHeard
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> HearingEvent.Failed(HearingFailure.NoPermission)
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> HearingEvent.Failed(HearingFailure.Busy)
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER,
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> HearingEvent.Failed(HearingFailure.Network)
        else -> HearingEvent.Failed(HearingFailure.Other)
    }

    private companion object {
        const val RMS_FLOOR = -2f
        const val RMS_CEILING = 10f
    }
}
