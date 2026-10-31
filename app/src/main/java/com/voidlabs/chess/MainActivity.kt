package com.voidlabs.chess

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var analyzeButton: Button

    private var engineProcess: Process? = null
    private var engineWriter: BufferedWriter? = null
    private val outputQueue = LinkedBlockingQueue<String>()

    @Volatile
    private var engineReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.rgb(18, 18, 24))
        }

        val title = TextView(this).apply {
            text = "CHESS ANALYZER"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Stockfish 19 · Offline Engine"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            text = "Engine: Loading..."
            textSize = 16f
            setTextColor(Color.YELLOW)
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 30)
        }

        analyzeButton = Button(this).apply {
            text = "START ANALYSIS"
            isEnabled = false
            setOnClickListener {
                analyzePosition()
            }
        }

        val info = TextView(this).apply {
            text = "Offline chess analysis\nStockfish 19 engine\nMove suggestions"
            textSize = 15f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 35, 0, 0)
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(statusText)
        root.addView(analyzeButton)
        root.addView(info)

        setContentView(root)

        loadEngine()
    }

    private fun loadEngine() {
        Thread {
            try {
                val engineDir = File(filesDir, "engine")
                engineDir.mkdirs()

                val engineFile = File(engineDir, "stockfish")

                if (!engineFile.exists() || engineFile.length() == 0L) {
                    assets.open("stockfish").use { input ->
                        engineFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                if (!engineFile.setExecutable(true, true) &&
                    !engineFile.canExecute()
                ) {
                    throw Exception("Cannot make Stockfish executable")
                }

                val process = ProcessBuilder(engineFile.absolutePath)
                    .redirectErrorStream(true)
                    .start()

                engineProcess = process

                engineWriter = BufferedWriter(
                    OutputStreamWriter(process.outputStream())
                )

                val reader = BufferedReader(
                    InputStreamReader(process.inputStream())
                )

                Thread {
                    try {
                        while (true) {
                            val line = reader.readLine() ?: break
                            outputQueue.offer(line)
                        }
                    } catch (_: Exception) {
                    } finally {
                        outputQueue.offer("__ENGINE_EXITED__")
                    }
                }.start()

                sendCommand("uci")
                waitForLine("uciok", 30)

                sendCommand("isready")
                waitForLine("readyok", 30)

                engineReady = true

                runOnUiThread {
                    statusText.text = "Engine: Stockfish 19 Ready"
                    statusText.setTextColor(Color.GREEN)
                    analyzeButton.isEnabled = true
                }

            } catch (e: Exception) {
                engineReady = false

                runOnUiThread {
                    statusText.text = "Engine Error:\n${e.message}"
                    statusText.setTextColor(Color.RED)
                }
            }
        }.start()
    }

    private fun sendCommand(command: String) {
        engineWriter?.apply {
            write(command)
            newLine()
            flush()
        } ?: throw Exception("Engine input unavailable")
    }

    private fun waitForLine(
        expected: String,
        timeoutSeconds: Long
    ) {
        val deadline = System.currentTimeMillis() +
            timeoutSeconds * 1000

        while (System.currentTimeMillis() < deadline) {
            val remaining = deadline - System.currentTimeMillis()

            val line = outputQueue.poll(
                remaining,
                TimeUnit.MILLISECONDS
            ) ?: break

            if (line == "__ENGINE_EXITED__") {
                throw Exception("Stockfish process stopped")
            }

            if (line.startsWith(expected)) return
        }

        throw Exception("Timeout waiting for $expected")
    }

    private fun analyzePosition() {
        if (!engineReady) return

        analyzeButton.isEnabled = false
        statusText.text = "Analyzing position..."
        statusText.setTextColor(Color.YELLOW)

        Thread {
            try {
                outputQueue.clear()

                sendCommand("ucinewgame")
                sendCommand("isready")
                waitForLine("readyok", 30)

                sendCommand("position startpos")
                sendCommand("go depth 12")

                var bestMove: String? = null
                var latestInfo = ""

                val deadline = System.currentTimeMillis() + 90000

                while (System.currentTimeMillis() < deadline) {
                    val line = outputQueue.poll(1, TimeUnit.SECONDS)
                        ?: continue

                    if (line == "__ENGINE_EXITED__") {
                        throw Exception("Stockfish stopped during analysis")
                    }

                    if (line.startsWith("info depth")) {
                        latestInfo = line
                    }

                    if (line.startsWith("bestmove")) {
                        bestMove = line
                        break
                    }
                }

                if (bestMove == null) {
                    throw Exception("Analysis timed out")
                }

                val result = bestMove.substringAfter("bestmove")
                    .trim()
                    .substringBefore(" ")

                runOnUiThread {
                    statusText.text =
                        "Best move: $result\n$latestInfo"
                    statusText.setTextColor(Color.GREEN)
                    analyzeButton.isEnabled = true
                }

            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "Analysis Error:\n${e.message}"
                    statusText.setTextColor(Color.RED)
                    analyzeButton.isEnabled = true
                }
            }
        }.start()
    }

    override fun onDestroy() {
        engineReady = false

        try {
            engineWriter?.write("quit")
            engineWriter?.newLine()
            engineWriter?.flush()
        } catch (_: Exception) {
        }

        engineProcess?.destroy()
        super.onDestroy()
    }
}
