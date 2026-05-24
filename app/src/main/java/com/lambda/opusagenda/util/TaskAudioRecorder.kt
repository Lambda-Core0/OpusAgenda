package com.lambda.opusagenda.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class TaskAudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    val isRecording: Boolean
        get() = recorder != null

    fun start(file: File): Boolean {
        cancel()
        outputFile = file
        val profiles = buildRecordingProfiles()
        for (profile in profiles) {
            val mediaRecorder = createMediaRecorder() ?: continue
            try {
                applyProfile(mediaRecorder, file, profile)
                mediaRecorder.prepare()
                mediaRecorder.start()
                recorder = mediaRecorder
                return true
            } catch (_: Exception) {
                try {
                    mediaRecorder.release()
                } catch (_: Exception) {
                }
            }
        }
        outputFile = null
        return false
    }

    fun stop(): File? {
        val file = outputFile
        try {
            recorder?.apply {
                try {
                    stop()
                } catch (_: Exception) {
                }
                release()
            }
        } catch (_: Exception) {
        } finally {
            recorder = null
            outputFile = null
        }
        return file?.takeIf { it.exists() && it.length() > 0L }
    }

    fun cancel() {
        try {
            recorder?.apply {
                try {
                    stop()
                } catch (_: Exception) {
                }
                release()
            }
        } catch (_: Exception) {
        } finally {
            recorder = null
        }
        outputFile?.takeIf { it.exists() }?.delete()
        outputFile = null
    }

    private fun createMediaRecorder(): MediaRecorder? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun applyProfile(recorder: MediaRecorder, file: File, profile: RecordingProfile) {
        recorder.setAudioSource(profile.audioSource)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        recorder.setAudioEncodingBitRate(profile.bitRate)
        recorder.setAudioSamplingRate(profile.sampleRate)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            recorder.setAudioChannels(profile.channels)
        }
        recorder.setOutputFile(file.absolutePath)
    }

    private fun buildRecordingProfiles(): List<RecordingProfile> {
        val sources = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                add(MediaRecorder.AudioSource.UNPROCESSED)
            }
            add(MediaRecorder.AudioSource.MIC)
        }

        val qualityTiers = listOf(
            RecordingProfile(sampleRate = 48_000, bitRate = 256_000, channels = 1),
            RecordingProfile(sampleRate = 48_000, bitRate = 192_000, channels = 1),
            RecordingProfile(sampleRate = 44_100, bitRate = 128_000, channels = 1),
        )

        return sources.flatMap { source ->
            qualityTiers.map { tier -> tier.copy(audioSource = source) }
        }
    }

    private data class RecordingProfile(
        val audioSource: Int = MediaRecorder.AudioSource.MIC,
        val sampleRate: Int,
        val bitRate: Int,
        val channels: Int,
    )
}
