package com.example.voicechanger

import android.annotation.SuppressLint
import android.media.*
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Local mode: on-device pitch shift. Remote mode: stream to AI server (RVC) and play the reply. */
class VoiceEngine {
    val shifter = PitchShifter()
    @Volatile private var running = false
    private var capture: Thread? = null
    private var playback: Thread? = null
    private var ws: WebSocket? = null
    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    private val queue = LinkedBlockingQueue<ByteArray>(200)

    var onStatus: (String) -> Unit = {}

    @SuppressLint("MissingPermission")
    fun start(remote: Boolean, serverUrl: String) {
        if (running) return
        running = true
        queue.clear()
        val sampleRate = if (remote) 16000 else 44100

        val minIn = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val minOut = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val chunkSamples = maxOf(minIn / 2, sampleRate / 10) // >= 100 ms
        val rec = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minIn, chunkSamples * 4))
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(minOut, chunkSamples * 4))
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        if (remote) {
            val req = Request.Builder().url(serverUrl).build()
            ws = client.newWebSocket(req, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send("""{"sample_rate":$sampleRate}""")
                    onStatus("Server connected")
                }
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    queue.offer(bytes.toByteArray())
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    onStatus("Server error: ${t.message}")
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    onStatus("Server closed")
                }
            })
            playback = Thread {
                track.play()
                while (running) {
                    val b = queue.poll(200, TimeUnit.MILLISECONDS) ?: continue
                    track.write(b, 0, b.size)
                }
                track.stop(); track.release()
            }.also { it.start() }
        }

        capture = Thread {
            val chunk = ShortArray(chunkSamples)
            rec.startRecording()
            if (!remote) track.play()
            while (running) {
                val n = rec.read(chunk, 0, chunk.size)
                if (n <= 0) continue
                if (remote) {
                    val bb = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
                    bb.asShortBuffer().put(chunk, 0, n)
                    ws?.send(bb.array().toByteString())
                } else {
                    shifter.process(chunk, n)
                    track.write(chunk, 0, n)
                }
            }
            rec.stop(); rec.release()
            if (!remote) { track.stop(); track.release() }
        }.also { it.start() }
    }

    fun stop() {
        running = false
        capture?.join(); playback?.join()
        capture = null; playback = null
        ws?.close(1000, "bye"); ws = null
    }
}
