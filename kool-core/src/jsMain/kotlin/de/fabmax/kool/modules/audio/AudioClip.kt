package de.fabmax.kool.modules.audio

import de.fabmax.kool.now
import org.w3c.dom.Audio

actual class AudioClip(val assetPath: String) {

    actual var volume = 1f
        set(value) {
            field = value
            latestClip.volume = value
        }

    actual var currentTime: Float
        get() = latestClip.currentTime
        set(value) {
            latestClip.currentTime = value
        }

    actual val duration: Float
        get() = latestClip.duration

    actual val isEnded: Boolean
        get() = latestClip.clipState == ClipState.STOPPED

    actual var loop: Boolean
        get() = latestClip.loop
        set(value) {
            latestClip.loop = value
        }

    private val clipPool = mutableListOf(ClipWrapper())
    private var latestClip = clipPool.first()
    private var lastPlay = Double.NEGATIVE_INFINITY

    private fun nextClip(): ClipWrapper {
        for (i in clipPool.indices) {
            if (clipPool[i].clipState == ClipState.STOPPED) {
                return clipPool[i]
            }
        }
        if (clipPool.size < MAX_CLIP_POOL_SIZE) {
            val clip = ClipWrapper()
            clipPool += clip
            return clip
        }
        return clipPool.minByOrNull { it.startTime }!!
    }

    actual fun play() {
        val t = now()
        if (t - lastPlay > MIN_PLAY_INTERVAL_MS) {
            lastPlay = t
            latestClip = nextClip().apply { play() }
        }
    }

    actual fun stop() {
        latestClip.stop()
    }

    companion object {
        const val MIN_PLAY_INTERVAL_MS = 150.0
        const val MAX_CLIP_POOL_SIZE = 5
    }

    private enum class ClipState {
        STOPPED,
        PLAYING
    }

    private inner class ClipWrapper {
        val audioElement = Audio(assetPath)

        var volume: Float
            get() = audioElement.volume.toFloat()
            set(value) { audioElement.volume = value.toDouble() }

        var currentTime: Float
            get() = audioElement.currentTime.toFloat()
            set(value) { audioElement.currentTime = value.toDouble() }

        val duration: Float
            get() = audioElement.duration.toFloat()

        val isEnded: Boolean
            get() = audioElement.ended

        var isPaused: Boolean = false
            private set

        var isStarted = false

        var loop: Boolean
            get() = audioElement.loop
            set(value) { audioElement.loop = value}

        var clipState = ClipState.STOPPED
            private set
        var startTime = 0.0

        init {
            volume = this@AudioClip.volume
            audioElement.onended = {
                clipState = ClipState.STOPPED
                true.asDynamic()
            }
        }

        fun play() {
            audioElement.pause()
            currentTime = 0f
            clipState = ClipState.PLAYING
            isStarted = true
            audioElement.play()
        }

        fun stop() {
            isPaused = true
            audioElement.pause()
            clipState = ClipState.STOPPED
        }
    }
}