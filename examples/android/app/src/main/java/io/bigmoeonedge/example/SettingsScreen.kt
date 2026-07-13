package io.bigmoeonedge.example

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Groups every tunable the engine exposes. Changes are applied to [current] live and
 * persisted by the caller. The layout mirrors the CLI flags one-to-one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(current: AppSettings, onChange: (AppSettings) -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section("Streaming") {
                // mmap is the no-streaming baseline. When on, every streaming knob below is
                // inert (the CLI omits --moe-stream and all sub-flags), so they are disabled.
                SwitchRow(
                    "mmap baseline (no streaming)",
                    "Load the whole model via llama.cpp mmap — the baseline to compare against",
                    current.mmap,
                ) { onChange(current.copy(mmap = it)) }

                val stream = !current.mmap
                val cacheOn = current.cacheMb == AppSettings.CACHE_AUTO || current.cacheMb > 0
                IntSetting(
                    "Expert cache (MiB)", AppSettings.CACHE_CHOICES, current.cacheMb,
                    format = {
                        when (it) {
                            AppSettings.CACHE_AUTO -> "Auto"
                            0 -> "off"
                            else -> "$it MiB"
                        }
                    },
                    enabled = stream,
                ) { onChange(current.copy(cacheMb = it)) }
                Text(
                    "Auto sizes the cache to free RAM and shrinks under pressure. Otherwise 0 or " +
                        "≥ 2000 MiB — a smaller fixed cache thrashes (evict + re-read) and is slower " +
                        "than off, so 1000 is intentionally not offered. Auto capped at ~4.6 GB is the " +
                        "measured sweet spot on 12 GB devices.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IntSetting(
                    "Auto cache ceiling (MiB)", AppSettings.CACHE_CEIL_CHOICES, current.cacheCeilMb,
                    format = { if (it == 0) "no cap" else "$it MiB" },
                    enabled = stream && current.cacheMb == AppSettings.CACHE_AUTO,
                ) { onChange(current.copy(cacheCeilMb = it)) }
                Text(
                    "Upper bound on the Auto budget. Past ~4.6 GB the extra hit rate no longer pays for " +
                        "the RAM, and uncapped Auto can over-grow into memory pressure and regress.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IntSetting("Parallel I/O lanes", AppSettings.IO_CHOICES, current.ioThreads, enabled = stream) {
                    onChange(current.copy(ioThreads = it))
                }
                Text(
                    "Parallel flash reads per token. 4 is the measured optimum on UFS4 — a real tok/s win.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SwitchRow(
                    "Direct I/O (O_DIRECT)",
                    "Bypass the page cache for expert reads. Keep on; falls back automatically if unsupported",
                    current.oDirect, enabled = stream,
                ) { onChange(current.copy(oDirect = it)) }
                SwitchRow(
                    "I/O–compute overlap",
                    "Read the next experts while the current layer computes — a measured tok/s win, part of the fast recipe",
                    current.overlap, enabled = stream,
                ) { onChange(current.copy(overlap = it)) }
                IntSetting(
                    "Temporal prefetch (layers)", AppSettings.PREFETCH_CHOICES, current.prefetchLayers,
                    format = { if (it == 0) "off" else "$it" },
                    enabled = stream && cacheOn,
                ) { onChange(current.copy(prefetchLayers = it)) }
                Text(
                    "Experimental. Read the next K layers' likely experts on idle lanes. Measured ±1% on " +
                        "this hardware (overlap already hides the read) — leave at 0 unless experimenting. Needs the cache on.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SwitchRow(
                    "Speculative gating",
                    "Experimental. Sharper next-layer predictor, but measured to HALVE steady-state tok/s here — off by default",
                    current.specGate, enabled = stream && cacheOn,
                ) { onChange(current.copy(specGate = it)) }
            }

            Section("Speed / quality") {
                // Active-expert (top-k) override is a load-time kv_override, valid in both streaming
                // and mmap mode, so it is not gated on the streamer.
                IntSetting(
                    "Active experts (top-k)", AppSettings.N_EXPERT_CHOICES, current.nExpertUsed,
                    format = {
                        when (it) {
                            0 -> "Model default"
                            6 -> "6 — turbo (+~23% tok/s)"
                            else -> "$it — fastest"
                        }
                    },
                ) { onChange(current.copy(nExpertUsed = it)) }
                Text(
                    "Route fewer experts per token than the model default (usually 8). +~23% tok/s at 6, " +
                        "but the output changes — a speed/quality trade-off, not a free win.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Section("Compute") {
                IntSetting("Compute threads", AppSettings.THREAD_CHOICES, current.threads) {
                    onChange(current.copy(threads = it))
                }
                IntSetting("Tokens to generate", AppSettings.NPREDICT_CHOICES, current.nPredict) {
                    onChange(current.copy(nPredict = it))
                }
            }

            Section("Prompt") {
                SwitchRow(
                    "Thinking", "Model reasoning; off passes --no-think (works for Qwen and Gemma)",
                    current.thinking,
                ) { onChange(current.copy(thinking = it)) }
            }

            Section("Debug") {
                SwitchRow(
                    "Load all experts", "Read every expert each token (A/B baseline; slow)",
                    current.loadAll, enabled = !current.mmap,
                ) { onChange(current.copy(loadAll = it)) }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        content()
    }
}
