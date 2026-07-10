package io.bigmoeonedge.example

import android.content.Context
import java.io.File

/**
 * Finds gguf models the user has pushed to the app's external files dir, e.g.
 *   adb push Qwen3-30B-A3B-Q4_K_M.gguf /sdcard/Android/data/io.bigmoeonedge.example/files/
 * and locates the streaming CLI shipped as a native library.
 */
object ModelManager {
    fun modelsDir(ctx: Context): File =
        ctx.getExternalFilesDir(null) ?: ctx.filesDir

    fun listModels(ctx: Context): List<File> =
        modelsDir(ctx).listFiles { f -> f.isFile && f.name.endsWith(".gguf") }
            ?.sortedBy { it.name }
            ?: emptyList()

    /** Absolute path of libbmoe-cli.so inside the app's nativeLibraryDir. */
    fun cliPath(ctx: Context): String =
        File(ctx.applicationInfo.nativeLibraryDir, "libbmoe-cli.so").absolutePath

    fun pushHint(ctx: Context): String =
        "No .gguf found. Push a model with:\n" +
            "adb push model.gguf ${modelsDir(ctx).absolutePath}/"
}
