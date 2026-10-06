package dev.pocketdeck.application

/** Result of an operation whose failure is an expected, modelled case rather than an exception. */
sealed interface Outcome<out S, out F> {
    data class Success<out S>(
        val value: S,
    ) : Outcome<S, Nothing>

    data class Failure<out F>(
        val reason: F,
    ) : Outcome<Nothing, F>
}
