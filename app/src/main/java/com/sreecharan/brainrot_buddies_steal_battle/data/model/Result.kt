package com.sreecharan.brainrot_buddies_steal_battle.data.model

/**
 * The single sealed result type for the data layer. Repositories return this
 * on every call; ViewModels map it into UiState. No raw exceptions cross the
 * repository boundary.
 */
sealed interface Result<out T> {
    data class Success<T>(
        val data: T,
    ) : Result<T>

    data class Error(
        val error: DataError,
    ) : Result<Nothing>

    data object Loading : Result<Nothing>
}

/** Typed data-layer failures. Carries messages, never throwables. */
sealed interface DataError {
    data class Storage(
        val message: String,
    ) : DataError

    data class NotFound(
        val what: String,
    ) : DataError

    data class Invalid(
        val reason: String,
    ) : DataError
}
