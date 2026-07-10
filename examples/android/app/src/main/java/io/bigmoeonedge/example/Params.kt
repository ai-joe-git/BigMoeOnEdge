package io.bigmoeonedge.example

/**
 * Streaming parameters the UI exposes, plus argv construction for bmoe-cli.
 *
 * Everything is passed as command-line arguments — never environment variables — so the
 * run is fully described by what you see here.
 */
data class Params(
    var cacheMb: Int = 4000,     // 0 = off; measured sweet spot on an 11 GB phone
    var ioThreads: Int = 4,      // parallel expert-read lanes
    var threads: Int = 4,        // compute threads (-t): the measured U-shape optimum
    var nPredict: Int = 48,
    var chatml: Boolean = true,
) {
    fun toArgv(cliPath: String, modelPath: String, prompt: String): List<String> {
        val a = mutableListOf(
            cliPath,
            "-m", modelPath,
            "-p", prompt,
            "-n", nPredict.toString(),
            "-t", threads.toString(),
            "--moe-stream",
            "--cache-mb", cacheMb.toString(),
            "--io-threads", ioThreads.toString(),
            "--progress",
        )
        if (chatml) a += "--chatml"
        return a
    }

    companion object {
        val CACHE_CHOICES = intArrayOf(0, 2000, 4000, 6000)
    }
}
