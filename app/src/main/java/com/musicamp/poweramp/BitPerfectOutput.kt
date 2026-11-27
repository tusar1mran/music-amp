package com.musicamp.poweramp

import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import android.os.Handler
import androidx.annotation.RequiresApi

/**
 * Bit-perfect output to a USB DAC (UAPP / Poweramp style), built on the Android 14+ mixer-attributes API.
 *
 * When engaged, the system mixer is bypassed for our stream: no resampling, no system volume scaling,
 * no audio effects, and other apps' sounds are not mixed into the DAC. The DAC is switched to the
 * current track's native sample rate on every track change.
 *
 * Limitation: decoding still goes through MediaPlayer, so the PCM it hands to the framework must match
 * the format we request (we prefer 16-bit, which is what MediaPlayer renders for most content).
 */
class BitPerfectOutput(
    private val audioManager: AudioManager,
    private val attributes: AudioAttributes,
    private val handler: Handler,
    private val onDevicesChanged: () -> Unit
) {
    enum class State { UNSUPPORTED_OS, NO_USB_DAC, DAC_NOT_CAPABLE, RATE_NOT_SUPPORTED, ACTIVE, OFF }

    var state: State = State.OFF
        private set
    var activeDescription: String = ""
        private set
    var usbDeviceName: String? = null
        private set

    private var engagedDevice: AudioDeviceInfo? = null

    val isOsSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            if (addedDevices?.any { it.isUsbSink() } == true) onDevicesChanged()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            if (removedDevices?.any { it.isUsbSink() } == true) {
                engagedDevice = null
                onDevicesChanged()
            }
        }
    }

    init {
        try { audioManager.registerAudioDeviceCallback(deviceCallback, handler) } catch (_: Exception) {}
    }

    private fun AudioDeviceInfo.isUsbSink(): Boolean =
        isSink && (type == AudioDeviceInfo.TYPE_USB_DEVICE || type == AudioDeviceInfo.TYPE_USB_HEADSET)

    private fun findUsbDac(): AudioDeviceInfo? = try {
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.isUsbSink() }
    } catch (_: Exception) {
        null
    }

    /** PCM encoding the DAC is running at while [State.ACTIVE]. */
    var activeEncoding: Int = AudioFormat.ENCODING_PCM_16BIT
        private set

    /**
     * Engages (or re-tunes) bit-perfect output for a track at [sampleRateHz]. Returns true when active.
     * [preferHighRes]: the native engine can feed 24/32-bit, so pick the deepest format; MediaPlayer renders
     * 16-bit, so prefer that for it.
     */
    fun engage(sampleRateHz: Int, preferHighRes: Boolean = false): Boolean {
        if (!isOsSupported) {
            state = State.UNSUPPORTED_OS; usbDeviceName = null; return false
        }
        return engageApi34(sampleRateHz, preferHighRes)
    }

    /** True if the connected DAC offers bit-perfect at [sampleRateHz] with at least 24 bits (needed for DoP). */
    fun supportsDeepFormatAt(sampleRateHz: Int): Boolean {
        if (!isOsSupported) return false
        return supportsDeepApi34(sampleRateHz)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun supportsDeepApi34(sampleRateHz: Int): Boolean {
        val dac = findUsbDac() ?: return false
        return try {
            audioManager.getSupportedMixerAttributes(dac).any {
                it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT &&
                    it.format.sampleRate == sampleRateHz &&
                    it.format.channelMask == AudioFormat.CHANNEL_OUT_STEREO &&
                    (it.format.encoding == AudioFormat.ENCODING_PCM_24BIT_PACKED || it.format.encoding == AudioFormat.ENCODING_PCM_32BIT)
            }
        } catch (_: Exception) {
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun engageApi34(sampleRateHz: Int, preferHighRes: Boolean): Boolean {
        val dac = findUsbDac()
        if (dac == null) {
            releaseApi34()
            state = State.NO_USB_DAC; usbDeviceName = null; activeDescription = ""
            return false
        }
        usbDeviceName = dac.productName?.toString()?.takeIf { it.isNotBlank() } ?: "USB DAC"
        val candidates = try {
            audioManager.getSupportedMixerAttributes(dac)
                .filter { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
        } catch (_: Exception) {
            emptyList()
        }
        if (candidates.isEmpty()) {
            releaseApi34()
            state = State.DAC_NOT_CAPABLE; activeDescription = ""
            return false
        }
        val stereo = AudioFormat.CHANNEL_OUT_STEREO
        val chosen = candidates
            .filter { it.format.sampleRate == sampleRateHz }
            .sortedWith(
                compareBy<AudioMixerAttributes>(
                    { if (it.format.channelMask == stereo) 0 else 1 },
                    { if (preferHighRes) highResRank(it.format.encoding) else encodingRank(it.format.encoding) }
                )
            )
            .firstOrNull()
        if (chosen == null) {
            releaseApi34()
            state = State.RATE_NOT_SUPPORTED; activeDescription = ""
            return false
        }
        val current = try { audioManager.getPreferredMixerAttributes(attributes, dac) } catch (_: Exception) { null }
        val ok = current == chosen || try {
            audioManager.setPreferredMixerAttributes(attributes, dac, chosen)
        } catch (_: Exception) {
            false
        }
        if (!ok) {
            state = State.DAC_NOT_CAPABLE; activeDescription = ""
            return false
        }
        engagedDevice = dac
        activeEncoding = chosen.format.encoding
        state = State.ACTIVE
        activeDescription = "${formatRate(chosen.format.sampleRate)} / ${encodingName(chosen.format.encoding)}"
        return true
    }

    /** Hands the DAC back to the system mixer. */
    fun release() {
        if (isOsSupported) releaseApi34()
        state = State.OFF
        activeDescription = ""
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun releaseApi34() {
        val dev = engagedDevice ?: return
        engagedDevice = null
        try { audioManager.clearPreferredMixerAttributes(attributes, dev) } catch (_: Exception) {}
    }

    fun dispose() {
        release()
        try { audioManager.unregisterAudioDeviceCallback(deviceCallback) } catch (_: Exception) {}
    }

    val statusText: String
        get() = when (state) {
            State.ACTIVE -> "Active on ${usbDeviceName ?: "USB DAC"} ($activeDescription)"
            State.UNSUPPORTED_OS -> "Needs Android 14 or newer"
            State.NO_USB_DAC -> "Waiting for a USB DAC"
            State.DAC_NOT_CAPABLE -> "${usbDeviceName ?: "This DAC"} does not offer a bit-perfect mode"
            State.RATE_NOT_SUPPORTED -> "${usbDeviceName ?: "DAC"} can't play this sample rate natively, using system mixer"
            State.OFF -> "Off"
        }

    private fun highResRank(enc: Int): Int = when (enc) {
        AudioFormat.ENCODING_PCM_32BIT -> 0
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 1
        AudioFormat.ENCODING_PCM_16BIT -> 2
        else -> 9 // float output is never bit-transparent for integer sources
    }

    private fun encodingRank(enc: Int): Int = when (enc) {
        AudioFormat.ENCODING_PCM_16BIT -> 0 // what MediaPlayer renders for most content
        AudioFormat.ENCODING_PCM_FLOAT -> 1
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 2
        AudioFormat.ENCODING_PCM_32BIT -> 3
        else -> 9
    }

    private fun encodingName(enc: Int): String = when (enc) {
        AudioFormat.ENCODING_PCM_16BIT -> "16-bit"
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> "24-bit"
        AudioFormat.ENCODING_PCM_32BIT -> "32-bit"
        AudioFormat.ENCODING_PCM_FLOAT -> "32-bit float"
        else -> "PCM"
    }

    private fun formatRate(hz: Int): String =
        if (hz % 1000 == 0) "${hz / 1000} kHz" else "%.1f kHz".format(hz / 1000f)
}
