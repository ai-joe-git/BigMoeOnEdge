package io.bigmoeonedge.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import java.util.Locale

/**
 * Minimal chat + live telemetry, in Compose. Pick a pushed .gguf, type a prompt, run:
 * the panel shows tok/s and the per-token compute-vs-flash-I/O split and cache hit rate
 * while the answer streams in. This is the use case that validates streaming a
 * larger-than-RAM MoE on the phone.
 */
class MainActivity : ComponentActivity() {

    private val requestNotif =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestAllFilesAccess()
        setContent {
            MaterialTheme(colorScheme = if (isSystemDark()) darkColorScheme() else lightColorScheme()) {
                Surface(color = MaterialTheme.colorScheme.background) { AppScreen() }
            }
        }
    }

    // gguf models are read in place from shared storage, which needs all-files access.
    private fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            runCatching {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName"),
                    )
                )
            }
        }
    }

    private fun isSystemDark(): Boolean {
        val flag = resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return flag == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScreen() {
    val context = LocalContext.current
    val ui by RunBus.state.collectAsStateWithLifecycle()

    var models by remember { mutableStateOf(ModelManager.listModels(context)) }
    var modelIdx by remember { mutableStateOf(0) }
    var cacheIdx by remember { mutableStateOf(Params.CACHE_CHOICES.indexOf(4000).coerceAtLeast(0)) }
    var prompt by rememberSaveable { mutableStateOf("Explain what a mixture-of-experts model is, in two sentences.") }

    fun refreshModels() {
        models = ModelManager.listModels(context)
        if (modelIdx >= models.size) modelIdx = 0
    }

    LaunchedEffect(Unit) { refreshModels() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("BigMoeOnEdge", fontSize = 22.sp, fontWeight = FontWeight.Bold)

        // model picker
        if (models.isEmpty()) {
            ElevatedCard {
                Text(
                    ModelManager.pushHint(),
                    Modifier.padding(12.dp),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            TextButton(onClick = { refreshModels() }) { Text("Refresh") }
        } else {
            LabeledDropdown(
                label = "Model",
                options = models.map { it.name },
                selected = modelIdx.coerceIn(0, models.size - 1),
                onSelect = { modelIdx = it },
            )
        }

        LabeledDropdown(
            label = "Expert cache",
            options = Params.CACHE_CHOICES.map { if (it == 0) "off" else "$it MiB" },
            selected = cacheIdx,
            onSelect = { cacheIdx = it },
        )

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Prompt") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    if (models.isNotEmpty()) {
                        startRun(context, models[modelIdx.coerceIn(0, models.size - 1)],
                            prompt.ifBlank { "The capital of Japan is" },
                            Params(cacheMb = Params.CACHE_CHOICES[cacheIdx]))
                    }
                },
                enabled = !ui.running && models.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { Text("Run") }

            OutlinedButton(
                onClick = {
                    context.startService(
                        Intent(context, RunService::class.java).setAction(RunService.ACTION_STOP)
                    )
                },
                enabled = ui.running,
                modifier = Modifier.weight(1f),
            ) { Text("Stop") }
        }

        TelemetryCard(ui)

        if (ui.answer.isNotEmpty()) {
            SelectionContainer {
                Text(ui.answer, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun TelemetryCard(ui: UiState) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (ui.error != null) {
                Text("error", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                Text(ui.error, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                return@Column
            }
            val t = ui.telemetry
            val hit = if (t.cacheHitPct >= 0) String.format(Locale.US, "%.0f%%", t.cacheHitPct) else "—"
            Text(
                String.format(Locale.US, "%.2f tok/s   (token %d/%d)", t.tokensPerSecond, t.step, t.steps),
                fontWeight = FontWeight.Bold, fontSize = 18.sp,
            )
            MeterRow("compute", t.computeMs, t.computeMs + t.ioMs, MaterialTheme.colorScheme.primary)
            MeterRow("flash I/O", t.ioMs, t.computeMs + t.ioMs, MaterialTheme.colorScheme.tertiary)
            Text("cache hit $hit", fontSize = 13.sp)
            if (ui.summary.isNotEmpty()) {
                Text(ui.summary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun MeterRow(label: String, value: Double, total: Double, color: androidx.compose.ui.graphics.Color) {
    val frac = if (total > 0) (value / total).toFloat().coerceIn(0f, 1f) else 0f
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 12.sp, modifier = Modifier.width(72.dp))
        LinearProgressIndicator(
            progress = { frac },
            color = color,
            modifier = Modifier.weight(1f).height(8.dp),
        )
        Text(String.format(Locale.US, "%.0f ms", value), fontSize = 12.sp, modifier = Modifier.width(56.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabeledDropdown(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = options.getOrElse(selected) { "" },
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { i, opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(i); expanded = false })
            }
        }
    }
}

private fun startRun(context: android.content.Context, model: File, prompt: String, params: Params) {
    val argv = ArrayList(params.toArgv(ModelManager.cliPath(context), model.absolutePath, prompt))
    val intent = Intent(context, RunService::class.java)
        .putExtra(RunService.EXTRA_MODEL, model.absolutePath)
        .putExtra(RunService.EXTRA_PROMPT, prompt)
        .putStringArrayListExtra(RunService.EXTRA_ARGV, argv)
    ContextCompat.startForegroundService(context, intent)
    RunBus.reset()
}
