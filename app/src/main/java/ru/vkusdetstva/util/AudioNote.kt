package ru.vkusdetstva.util

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import java.io.File
import java.util.UUID

class AudioNote(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var path: String? = null

    fun start() {
        check(recorder == null)
        val folder = File(context.filesDir, "audio").apply { mkdirs() }
        path = File(folder, "${UUID.randomUUID()}.m4a").absolutePath
        @Suppress("DEPRECATION")
        val device = MediaRecorder()
        try {
            device.setAudioSource(MediaRecorder.AudioSource.MIC)
            device.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            device.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            device.setOutputFile(path)
            device.prepare()
            device.start()
            recorder = device
        } catch (error: Exception) {
            device.release()
            path?.let { File(it).delete() }
            path = null
            throw error
        }
    }

    fun stop(): String? {
        val device = recorder ?: return null
        recorder = null
        return try { device.stop(); path } catch (_: RuntimeException) {
            path?.let { File(it).delete() }; null
        } finally { device.release(); path = null }
    }

    fun play(file: String) {
        player?.release()
        player = MediaPlayer().apply {
            setDataSource(file)
            setOnCompletionListener { it.release(); if (player === it) player = null }
            prepare()
            start()
        }
    }

    fun release() { if (recorder != null) runCatching { stop() }; player?.release(); player = null }
}
