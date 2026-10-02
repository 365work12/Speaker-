package com.example.network

import android.os.Build
import android.util.Log
import com.example.audio.AudioPlaybackManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

class DesktopWebServer(
    private val audioPlaybackManager: AudioPlaybackManager,
    private val onClientConnected: (clientName: String, clientIp: String) -> Unit,
    private val onClientDisconnected: () -> Unit,
    private val onStatsUpdated: (bytesReceived: Long, durationSec: Long) -> Unit,
    private val onPingMeasured: (latencyMs: Long) -> Unit
) {
    private val TAG = "DesktopWebServer"
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var isRunning = false
    private var totalBytesReceived = 0L
    private var streamStartTime = 0L

    fun start(port: Int = 8080, scope: CoroutineScope) {
        if (isRunning) return
        isRunning = true

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                Log.d(TAG, "Desktop Web Streamer listening on port $port")

                while (isActive && isRunning) {
                    val clientSocket = try {
                        serverSocket?.accept() ?: break
                    } catch (e: SocketException) {
                        break
                    }

                    launch(Dispatchers.IO) {
                        handleHttpRequest(clientSocket)
                    }
                }
            } catch (e: CancellationException) {
            } catch (e: Exception) {
                Log.e(TAG, "Error in Desktop Web Server", e)
            } finally {
                stop()
            }
        }
    }

    private fun handleHttpRequest(socket: Socket) {
        try {
            socket.tcpNoDelay = true
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            val reader = BufferedReader(InputStreamReader(input))

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val path = parts[1]

            // Read HTTP headers
            var line: String?
            var contentLength = 0
            var contentType = ""

            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrEmpty()) break
                val lower = line!!.lowercase()
                if (lower.startsWith("content-length:")) {
                    contentLength = lower.substringAfter(":").trim().toIntOrNull() ?: 0
                } else if (lower.startsWith("content-type:")) {
                    contentType = lower.substringAfter(":").trim()
                }
            }

            val clientIp = socket.inetAddress.hostAddress ?: "Unknown"

            when {
                path == "/" || path.startsWith("/index") -> {
                    // Serve the Web Sender HTML App
                    val html = getWebSenderHtml()
                    val responseBytes = html.toByteArray(Charsets.UTF_8)
                    val header = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/html; charset=UTF-8\r\n" +
                            "Content-Length: ${responseBytes.size}\r\n" +
                            "Connection: close\r\n" +
                            "Access-Control-Allow-Origin: *\r\n\r\n"
                    output.write(header.toByteArray(Charsets.UTF_8))
                    output.write(responseBytes)
                    output.flush()
                }

                path.startsWith("/ping") -> {
                    val clientTimeStr = if (path.contains("t=")) path.substringAfter("t=").substringBefore("&") else null
                    val clientTime = clientTimeStr?.toLongOrNull() ?: 0L
                    if (clientTime > 0) {
                        val latency = System.currentTimeMillis() - clientTime
                        if (latency in 1..2000) {
                            onPingMeasured(latency)
                        }
                    }

                    val resp = "{\"status\":\"ok\",\"serverTime\":${System.currentTimeMillis()}}"
                    val respBytes = resp.toByteArray(Charsets.UTF_8)
                    val header = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: ${respBytes.size}\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Connection: close\r\n\r\n"
                    output.write(header.toByteArray(Charsets.UTF_8))
                    output.write(respBytes)
                    output.flush()
                }

                path.startsWith("/stream_pcm") && method == "POST" -> {
                    if (streamStartTime == 0L) {
                        streamStartTime = System.currentTimeMillis()
                        audioPlaybackManager.start()
                        onClientConnected("PC Desktop ($clientIp)", clientIp)
                    }

                    val buffer = ByteArray(4096)
                    var bytesRemaining = contentLength
                    val isChunked = contentLength <= 0

                    if (!isChunked) {
                        while (bytesRemaining > 0) {
                            val toRead = minOf(buffer.size, bytesRemaining)
                            val read = input.read(buffer, 0, toRead)
                            if (read <= 0) break
                            audioPlaybackManager.writePcm(buffer, 0, read)
                            totalBytesReceived += read
                            bytesRemaining -= read
                        }
                    } else {
                        while (true) {
                            val read = input.read(buffer, 0, buffer.size)
                            if (read <= 0) break
                            audioPlaybackManager.writePcm(buffer, 0, read)
                            totalBytesReceived += read
                            val durationSec = (System.currentTimeMillis() - streamStartTime) / 1000L
                            onStatsUpdated(totalBytesReceived, durationSec)
                        }
                    }

                    val durationSec = (System.currentTimeMillis() - streamStartTime) / 1000L
                    onStatsUpdated(totalBytesReceived, durationSec)

                    val resp = "{\"status\":\"ok\",\"bytes\":$totalBytesReceived}"
                    val respBytes = resp.toByteArray(Charsets.UTF_8)
                    val header = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: ${respBytes.size}\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Connection: close\r\n\r\n"
                    output.write(header.toByteArray(Charsets.UTF_8))
                    output.write(respBytes)
                    output.flush()
                }

                path.startsWith("/stop") -> {
                    audioPlaybackManager.stop()
                    streamStartTime = 0L
                    onClientDisconnected()
                    val resp = "{\"status\":\"stopped\"}"
                    val respBytes = resp.toByteArray(Charsets.UTF_8)
                    val header = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: ${respBytes.size}\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Connection: close\r\n\r\n"
                    output.write(header.toByteArray(Charsets.UTF_8))
                    output.write(respBytes)
                    output.flush()
                }

                else -> {
                    val notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n"
                    output.write(notFound.toByteArray(Charsets.UTF_8))
                    output.flush()
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
    }

    private fun getWebSenderHtml(): String {
        val model = "${Build.MANUFACTURER} ${Build.MODEL}"
        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>SoundLink — PC Desktop Web Sender</title>
<style>
  :root {
    --primary: #38bdf8;
    --primary-hover: #0284c7;
    --bg: #0f172a;
    --card: #1e293b;
    --text: #f8fafc;
    --text-muted: #94a3b8;
    --success: #10b981;
    --danger: #ef4444;
  }
  * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; }
  body { background: var(--bg); color: var(--text); padding: 24px; min-height: 100vh; display: flex; flex-direction: column; align-items: center; justify-content: center; }
  .container { max-width: 560px; width: 100%; background: var(--card); border-radius: 24px; padding: 32px; box-shadow: 0 20px 40px rgba(0,0,0,0.6); border: 1px solid #334155; }
  .header { text-align: center; margin-bottom: 24px; }
  .header h1 { font-size: 26px; font-weight: 700; color: #fff; display: flex; align-items: center; justify-content: center; gap: 8px; }
  .header p { font-size: 14px; color: var(--text-muted); margin-top: 6px; }
  .link-pill { display: inline-flex; align-items: center; gap: 8px; background: rgba(56,189,248,0.12); color: var(--primary); padding: 6px 14px; border-radius: 999px; font-size: 12px; font-weight: 600; margin-top: 10px; font-family: monospace; border: 1px solid rgba(56,189,248,0.25); }
  .btn-grid { display: flex; flex-direction: column; gap: 12px; margin: 20px 0; }
  .btn { display: flex; align-items: center; justify-content: center; gap: 10px; padding: 14px 20px; border-radius: 14px; border: none; font-size: 15px; font-weight: 600; cursor: pointer; transition: all 0.2s; background: #334155; color: #fff; }
  .btn:hover { filter: brightness(1.12); transform: translateY(-1px); }
  .btn-primary { background: var(--primary); color: #0f172a; }
  .btn-secondary { background: #475569; color: #fff; }
  .btn-danger { background: var(--danger); color: #fff; }
  .volume-row { display: flex; align-items: center; gap: 12px; margin: 16px 0; background: rgba(0,0,0,0.2); padding: 12px 16px; border-radius: 12px; border: 1px solid #334155; }
  .volume-row label { font-size: 13px; font-weight: 600; color: var(--text-muted); }
  .volume-slider { flex: 1; accent-color: var(--primary); cursor: pointer; }
  .status-box { background: rgba(0,0,0,0.3); border-radius: 14px; padding: 16px; margin-top: 16px; border: 1px solid #334155; font-size: 13px; }
  .metric-row { display: flex; justify-content: space-between; margin-bottom: 6px; }
  .metric-label { color: var(--text-muted); }
  .metric-value { font-weight: 600; color: #fff; font-family: monospace; }
  .visualizer { display: flex; gap: 4px; height: 38px; align-items: center; justify-content: center; margin: 16px 0 8px 0; }
  .bar { width: 5px; height: 6px; background: var(--primary); border-radius: 2px; transition: height 0.08s ease; }
  .tip-box { font-size: 12px; color: var(--text-muted); text-align: center; margin-top: 16px; line-height: 18px; }
  input[type="file"] { display: none; }
</style>
</head>
<body>
<div class="container">
  <div class="header">
    <h1>🔊 SoundLink</h1>
    <p>Stream Windows PC / Mac System Audio to <strong>$model</strong></p>
    <div class="link-pill">
      <span>🔗 http://soundlink.local:8080</span>
    </div>
  </div>

  <div class="btn-grid">
    <button class="btn btn-primary" id="btnShareScreen">🖥️ Share PC System / Tab Audio</button>
    <button class="btn btn-secondary" id="btnMic">🎤 Stream PC Microphone</button>
    <button class="btn btn-secondary" id="btnMusicFile">🎵 Stream Local Music File (MP3/WAV)</button>
    <button class="btn btn-secondary" id="btnSynth">⚡ Test Synth Beats & Melody</button>
    <button class="btn btn-danger" id="btnStop" style="display:none;">⏹️ Stop Audio Stream</button>
  </div>
  <input type="file" id="fileInput" accept="audio/*">

  <div class="volume-row">
    <label>Volume</label>
    <input type="range" class="volume-slider" id="volSlider" min="0" max="100" value="90">
    <span id="volVal" style="font-family:monospace; font-size:12px; font-weight:bold;">90%</span>
  </div>

  <div class="visualizer" id="visualizer">
    <div class="bar"></div><div class="bar"></div><div class="bar"></div><div class="bar"></div>
    <div class="bar"></div><div class="bar"></div><div class="bar"></div><div class="bar"></div>
    <div class="bar"></div><div class="bar"></div><div class="bar"></div><div class="bar"></div>
    <div class="bar"></div><div class="bar"></div><div class="bar"></div><div class="bar"></div>
  </div>

  <div class="status-box">
    <div class="metric-row">
      <span class="metric-label">Network Latency (RTT):</span>
      <span class="metric-value" id="pingVal">-- ms</span>
    </div>
    <div class="metric-row">
      <span class="metric-label">Packet Jitter Variance:</span>
      <span class="metric-value" id="jitterVal">-- ms</span>
    </div>
    <div class="metric-row">
      <span class="metric-label">Audio Quality:</span>
      <span class="metric-value">44.1 kHz, 16-bit PCM</span>
    </div>
    <div class="metric-row">
      <span class="metric-label">Data Transferred:</span>
      <span class="metric-value" id="bytesVal">0 KB</span>
    </div>
  </div>

  <div class="tip-box">
    💡 <strong>Tip for PC Audio:</strong> When clicking "Share PC System Audio", select <em>Entire screen</em> or <em>Chrome Tab</em> and check the <strong>"Share audio"</strong> checkbox in the browser prompt!
  </div>
</div>

<script>
  let audioCtx = null;
  let activeStream = null;
  let processorNode = null;
  let synthInterval = null;
  let isStreaming = false;
  let totalBytes = 0;
  let lastRtt = 10;
  let jitter = 1.5;
  let streamGain = 0.9;

  const btnShareScreen = document.getElementById('btnShareScreen');
  const btnMic = document.getElementById('btnMic');
  const btnMusicFile = document.getElementById('btnMusicFile');
  const btnSynth = document.getElementById('btnSynth');
  const btnStop = document.getElementById('btnStop');
  const fileInput = document.getElementById('fileInput');
  const volSlider = document.getElementById('volSlider');
  const volVal = document.getElementById('volVal');
  const pingVal = document.getElementById('pingVal');
  const jitterVal = document.getElementById('jitterVal');
  const bytesVal = document.getElementById('bytesVal');
  const bars = document.querySelectorAll('.bar');

  volSlider.addEventListener('input', (e) => {
    streamGain = e.target.value / 100;
    volVal.textContent = e.target.value + '%';
  });

  // Measure RTT latency and Jitter every second
  setInterval(async () => {
    try {
      const t0 = performance.now();
      const res = await fetch('/ping?t=' + Date.now());
      if (res.ok) {
        const rtt = Math.round(performance.now() - t0);
        const d = Math.abs(rtt - lastRtt);
        jitter = jitter + (d - jitter) / 16.0;
        lastRtt = rtt;
        pingVal.textContent = rtt + ' ms';
        jitterVal.textContent = jitter.toFixed(1) + ' ms';
      }
    } catch (_) {}
  }, 1000);

  function setStreamingUi(active) {
    isStreaming = active;
    btnShareScreen.style.display = active ? 'none' : 'flex';
    btnMic.style.display = active ? 'none' : 'flex';
    btnMusicFile.style.display = active ? 'none' : 'flex';
    btnSynth.style.display = active ? 'none' : 'flex';
    btnStop.style.display = active ? 'flex' : 'none';
    if (!active) {
      bars.forEach(b => b.style.height = '6px');
    }
  }

  async function startStreamingFromMediaStream(stream) {
    activeStream = stream;
    audioCtx = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: 44100 });
    const source = audioCtx.createMediaStreamSource(stream);

    processorNode = audioCtx.createScriptProcessor(2048, 1, 1);
    source.connect(processorNode);
    processorNode.connect(audioCtx.destination);

    setStreamingUi(true);

    processorNode.onaudioprocess = async (e) => {
      if (!isStreaming) return;
      const inputData = e.inputBuffer.getChannelData(0);
      const pcm16 = new Int16Array(inputData.length);
      let sum = 0;

      for (let i = 0; i < inputData.length; i++) {
        const s = Math.max(-1, Math.min(1, inputData[i] * streamGain));
        pcm16[i] = s < 0 ? s * 0x8000 : s * 0x7FFF;
        sum += s * s;
      }

      const rms = Math.sqrt(sum / inputData.length);
      bars.forEach((bar, idx) => {
        const h = Math.min(36, Math.max(4, rms * 110 * (0.8 + 0.4 * Math.sin(idx))));
        bar.style.height = h + 'px';
      });

      try {
        await fetch('/stream_pcm', {
          method: 'POST',
          body: pcm16.buffer,
          headers: { 'Content-Type': 'application/octet-stream' }
        });
        totalBytes += pcm16.byteLength;
        bytesVal.textContent = (totalBytes / 1024).toFixed(1) + ' KB';
      } catch (err) {}
    };
  }

  btnShareScreen.addEventListener('click', async () => {
    try {
      const stream = await navigator.mediaDevices.getDisplayMedia({ video: true, audio: true });
      if (stream.getAudioTracks().length === 0) {
        alert("Please make sure to check 'Share audio' when choosing your screen or tab!");
        stream.getTracks().forEach(t => t.stop());
        return;
      }
      startStreamingFromMediaStream(stream);
    } catch (err) {
      alert("Could not capture desktop audio: " + err.message);
    }
  });

  btnMic.addEventListener('click', async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      startStreamingFromMediaStream(stream);
    } catch (err) {
      alert("Microphone access denied: " + err.message);
    }
  });

  btnMusicFile.addEventListener('click', () => { fileInput.click(); });

  fileInput.addEventListener('change', async (e) => {
    const file = e.target.files[0];
    if (!file) return;

    audioCtx = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: 44100 });
    const arrayBuffer = await file.arrayBuffer();
    const audioBuffer = await audioCtx.decodeAudioData(arrayBuffer);

    const source = audioCtx.createBufferSource();
    source.buffer = audioBuffer;
    source.loop = true;

    processorNode = audioCtx.createScriptProcessor(2048, 1, 1);
    source.connect(processorNode);
    processorNode.connect(audioCtx.destination);
    source.start();

    setStreamingUi(true);

    processorNode.onaudioprocess = async (ev) => {
      if (!isStreaming) return;
      const inputData = ev.inputBuffer.getChannelData(0);
      const pcm16 = new Int16Array(inputData.length);
      let sum = 0;
      for (let i = 0; i < inputData.length; i++) {
        const s = Math.max(-1, Math.min(1, inputData[i] * streamGain));
        pcm16[i] = s < 0 ? s * 0x8000 : s * 0x7FFF;
        sum += s * s;
      }
      const rms = Math.sqrt(sum / inputData.length);
      bars.forEach((bar, idx) => {
        const h = Math.min(36, Math.max(4, rms * 110 * (0.8 + 0.4 * Math.sin(idx))));
        bar.style.height = h + 'px';
      });
      try {
        await fetch('/stream_pcm', {
          method: 'POST',
          body: pcm16.buffer,
          headers: { 'Content-Type': 'application/octet-stream' }
        });
        totalBytes += pcm16.byteLength;
        bytesVal.textContent = (totalBytes / 1024).toFixed(1) + ' KB';
      } catch (err) {}
    };
  });

  btnSynth.addEventListener('click', () => {
    setStreamingUi(true);
    let phase = 0;
    const notes = [261.63, 329.63, 392.0, 523.25, 440.0, 392.0];
    let noteIdx = 0;
    let sampleCount = 0;

    synthInterval = setInterval(async () => {
      if (!isStreaming) return;
      const samples = 1024;
      const pcm16 = new Int16Array(samples);
      const freq = notes[noteIdx];
      const delta = (2 * Math.PI * freq) / 44100;

      for (let i = 0; i < samples; i++) {
        phase += delta;
        const wave = Math.sin(phase) * 0.5 * streamGain;
        pcm16[i] = wave < 0 ? wave * 0x8000 : wave * 0x7FFF;
      }
      sampleCount += samples;
      if (sampleCount > 8820) {
        sampleCount = 0;
        noteIdx = (noteIdx + 1) % notes.length;
      }

      bars.forEach((bar, idx) => {
        bar.style.height = (8 + Math.sin(phase + idx) * 16) + 'px';
      });

      try {
        await fetch('/stream_pcm', {
          method: 'POST',
          body: pcm16.buffer,
          headers: { 'Content-Type': 'application/octet-stream' }
        });
        totalBytes += pcm16.byteLength;
        bytesVal.textContent = (totalBytes / 1024).toFixed(1) + ' KB';
      } catch (err) {}
    }, 23);
  });

  btnStop.addEventListener('click', () => {
    isStreaming = false;
    if (synthInterval) { clearInterval(synthInterval); synthInterval = null; }
    if (activeStream) {
      activeStream.getTracks().forEach(t => t.stop());
      activeStream = null;
    }
    if (processorNode) processorNode.disconnect();
    if (audioCtx) audioCtx.close();

    fetch('/stop');
    setStreamingUi(false);
  });
</script>
</body>
</html>
        """.trimIndent()
    }
}
