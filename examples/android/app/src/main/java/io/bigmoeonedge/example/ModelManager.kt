package io.bigmoeonedge.example

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Finds gguf models on shared storage. A gguf can be tens of GB, so the app reads it in
 * place (via the bmoe-cli subprocess) rather than copying it into app storage — which
 * requires all-files access (see [StoragePermission]). Scans the public Downloads folder
 * plus the app's own external files dir.
 *
 *   adb push Qwen3-30B-A3B-Q4_K_M.gguf /sdcard/Download/
 */
object ModelManager {
    private fun scanDirs(ctx: Context): List<File> = buildList {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let { add(it) }
        ctx.getExternalFilesDir(null)?.let { add(it) }
    }

    fun listModels(ctx: Context): List<File> =
        scanDirs(ctx)
            .flatMap { it.listFiles { f -> f.isFile && f.name.endsWith(".gguf") }?.toList() ?: emptyList() }
            .distinctBy { it.absolutePath }
            .sortedBy { it.name }

    /** Absolute path of libbmoe-cli.so inside the app's nativeLibraryDir. */
    fun cliPath(ctx: Context): String =
        File(ctx.applicationInfo.nativeLibraryDir, "libbmoe-cli.so").absolutePath

    fun pushHint(): String =
        "No .gguf found. Grant all-files access, then push a model with:\n" +
            "adb push model.gguf /sdcard/Download/"
}
