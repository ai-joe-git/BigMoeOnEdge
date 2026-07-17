# BigMoeOnEdge — Android example

A minimal chat app that validates the throughput claim on a real phone: pick a pushed
`.gguf`, type a prompt, and watch the answer stream in while a live panel shows tok/s and
the per-token compute-vs-flash-I/O split and cache hit rate.

It runs the engine as the `bmoe-cli` binary (shipped as `libbmoe-cli.so`) via
`ProcessBuilder` from a foreground service — no JNI. This is the same pattern used by the
research harness and keeps the app a thin driver over the CLI.

## Build

1. Cross-compile and stage the engine binaries (needs the Android NDK):

   ```powershell
   pwsh ../../scripts/build-android.ps1
   ```

   This fills `app/src/main/jniLibs/arm64-v8a/` with `libbmoe-cli.so` and the
   `libllama`/`libggml` shared libraries.

2. Build and install the APK. Open this folder in Android Studio, or use the committed
   Gradle wrapper directly. The app has two distribution flavors (see below); build the one
   you want:

   ```bash
   ./gradlew assembleDevDebug
   adb install app/build/outputs/apk/dev/debug/app-dev-debug.apk
   ```

## Flavors

Two build flavors differ only in how a model reaches the device:

- **dev** — sideloaded (this is what CI attaches to releases). Keeps all-files access, so it
  can also read a model adb-pushed to shared storage. Application id `…​.example.dev`.
- **play** — Play-Store-compliant. No broad storage permission: models come only through the
  in-app downloader or the file picker. `./gradlew assemblePlayDebug`.

## Getting a model onto the device

The picker lists every MoE `.gguf` it finds (dense models are filtered out by a gguf-header
check). Nothing below needs a storage permission except the last option.

1. **Built-in catalog** (both flavors) — the "Get a model" card offers the models this engine
   is measured on, each a single tap: **Qwen3-30B-A3B-Q4_K_M** (~18.5 GB, the reference model)
   and **Gemma-4-26B-A4B-it-Q4_K_M** (~17 GB). Downloads run in the background, survive the app
   being killed, and appear in the picker when done.
2. **Any other model** — under **Other model**, paste a direct gguf URL (e.g. a Hugging Face
   `…/resolve/main/model.gguf` link), or pick a `.gguf` already on the device to import it.
3. **adb push** (dev flavor only — needs all-files access, which the dev build requests):

   ```bash
   adb push Qwen3-30B-A3B-Q4_K_M.gguf /sdcard/Download/
   # /data/local/tmp/bmoe avoids duplicating a model too big to copy, and is on a real
   # filesystem where O_DIRECT works (the emulated dirs fall back to buffered I/O)
   adb push Qwen3-30B-A3B-Q4_K_M.gguf /data/local/tmp/bmoe/
   ```

   This directory was named `shardllm` before v0.8.0. To keep models already pushed there:

   ```bash
   adb shell mv /data/local/tmp/shardllm /data/local/tmp/bmoe
   ```

### gpt-oss-120b

Listed in the catalog but not downloadable in-app: Hugging Face ships the Q4_K_M quant as two
shards (the 50 GB per-file limit), and expert streaming reads tensors by byte offset from a
single file. Merge the shards on a PC, then transfer the result:

```bash
llama-gguf-split --merge gpt-oss-120b-Q4_K_M-00001-of-00002.gguf gpt-oss-120b-Q4_K_M.gguf
adb push gpt-oss-120b-Q4_K_M.gguf /data/local/tmp/bmoe/   # or import it with the file picker
```

## Expected numbers

On a phone with UFS 4.x storage and ~12 GB RAM, streaming Qwen3-30B-A3B-Q4_K_M with the
expert cache at 4000 MiB, 4 I/O lanes and 4 compute threads, decode settles around
**0.55–0.6 s/token (~1.8 tok/s)** — a model ~1.7× the device RAM, lossless. See
`../../docs/benchmark-method.md` for the full procedure and the cache/thread sweep.
