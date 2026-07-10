package io.bigmoeonedge.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.bigmoeonedge.example.databinding.ActivityMainBinding
import java.io.File
import java.util.Locale

/**
 * Minimal chat + live telemetry. Pick a pushed .gguf, type a prompt, run: the panel
 * shows tok/s and the per-token compute-vs-flash-I/O split and cache hit rate while the
 * answer streams in. This is the use case that validates ~2 tok/s on a 30B-class MoE.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val ui = Handler(Looper.getMainLooper())
    private var models: List<File> = emptyList()
    private val params = Params()

    private val poll = object : Runnable {
        override fun run() {
            renderState()
            ui.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        setupModelSpinner()
        setupCacheSpinner()

        b.runButton.setOnClickListener { startRun() }
        b.stopButton.setOnClickListener {
            startService(Intent(this, RunService::class.java).setAction(RunService.ACTION_STOP))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshModels()
        ui.post(poll)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(poll)
    }

    private fun setupModelSpinner() {
        b.modelSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, mutableListOf<String>()
        )
    }

    private fun setupCacheSpinner() {
        val labels = Params.CACHE_CHOICES.map { if (it == 0) "off" else "$it MiB" }
        b.cacheSpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        val idx = Params.CACHE_CHOICES.indexOf(params.cacheMb).coerceAtLeast(0)
        b.cacheSpinner.setSelection(idx)
    }

    private fun refreshModels() {
        models = ModelManager.listModels(this)
        @Suppress("UNCHECKED_CAST")
        val adapter = b.modelSpinner.adapter as ArrayAdapter<String>
        adapter.clear()
        adapter.addAll(models.map { it.name })
        adapter.notifyDataSetChanged()
        b.modelHint.text = if (models.isEmpty()) ModelManager.pushHint(this) else ""
        b.runButton.isEnabled = models.isNotEmpty()
    }

    private fun startRun() {
        val pos = b.modelSpinner.selectedItemPosition
        if (pos < 0 || pos >= models.size) return
        val model = models[pos]
        val prompt = b.promptInput.text.toString().ifBlank { "The capital of Japan is" }
        params.cacheMb = Params.CACHE_CHOICES[b.cacheSpinner.selectedItemPosition]

        val argv = ArrayList(params.toArgv(ModelManager.cliPath(this), model.absolutePath, prompt))
        val intent = Intent(this, RunService::class.java)
            .putExtra(RunService.EXTRA_MODEL, model.absolutePath)
            .putExtra(RunService.EXTRA_PROMPT, prompt)
            .putStringArrayListExtra(RunService.EXTRA_ARGV, argv)
        ContextCompat.startForegroundService(this, intent)

        RunBus.reset()
        b.answerView.text = ""
    }

    private fun renderState() {
        val running = RunBus.running.get()
        b.runButton.isEnabled = !running && models.isNotEmpty()
        b.stopButton.isEnabled = running

        RunBus.error?.let {
            b.telemetryView.text = "error:\n$it"
            return
        }

        val t = RunBus.telemetry
        val hit = if (t.cacheHitPct >= 0) String.format(Locale.US, "%.0f%%", t.cacheHitPct) else "—"
        b.telemetryView.text = String.format(
            Locale.US,
            "token %d/%d   %.2f tok/s\ncompute %.0f ms   flash I/O %.0f ms   cache hit %s\n%s",
            t.step, t.steps, t.tokensPerSecond, t.computeMs, t.ioMs, hit, RunBus.summary
        )
        if (RunBus.answer.isNotEmpty()) b.answerView.text = RunBus.answer
    }
}
