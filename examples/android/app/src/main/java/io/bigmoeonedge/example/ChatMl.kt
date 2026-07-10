package io.bigmoeonedge.example

/**
 * Builds a Qwen-family ChatML prompt. The CLI also accepts --chatml for a single turn;
 * this helper is here for multi-turn history if the UI grows one. Kept trivial on
 * purpose — the engine owns tokenization.
 */
object ChatMl {
    fun singleTurn(user: String): String =
        "<|im_start|>user\n$user<|im_end|>\n<|im_start|>assistant\n"
}
