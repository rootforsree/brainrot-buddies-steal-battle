package com.sreecharan.brainrot_buddies_steal_battle.ui.poster

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sreecharan.brainrot_buddies_steal_battle.data.model.Result
import com.sreecharan.brainrot_buddies_steal_battle.data.repository.GameRepository
import com.sreecharan.brainrot_buddies_steal_battle.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

private fun <T> Result<T>.dataOrNull(): T? = (this as? Result.Success<T>)?.data

private const val POSTER_DIR = "posters"
private const val POSTER_FILE = "poster.jpg"
private const val PREVIEW_FILE = "poster_preview.jpg"
private const val POSTER_SIZE = 1024

/** Cap for the bitmap downsampling factor. */
private const val MAX_SAMPLE_SIZE = 16

/** JPEG quality for the stored poster preview. */
private const val JPEG_QUALITY = 90

/**
 * "My Poster": local-only photo import (contract §1).
 * - Pick -> center-crop square preview -> confirm -> saved to app-private
 *   internal storage.
 * - EXIF/GPS is stripped by construction: the saved file is a freshly
 *   re-encoded bitmap, never a copy of the original.
 * - The image NEVER leaves the device (no upload, sync, share, or trade)
 *   and is deleted with data-clear.
 */
@Immutable
data class PosterUiState(
    val isLoading: Boolean = true,
    val hasPoster: Boolean = false,
    /** Repo-relative path of the saved poster ("" when none). */
    val posterRelativePath: String = "",
    /** Absolute path of the unsaved preview in cacheDir, if any. */
    val previewPath: String? = null,
    val isSaving: Boolean = false,
    val failed: Boolean = false,
)

@HiltViewModel
class PosterViewModel
    @Inject
    constructor(
        private val repository: GameRepository,
        @ApplicationContext private val context: Context,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val previewState = MutableStateFlow<String?>(null)
        private val workingState = MutableStateFlow(Working.IDLE)

        private enum class Working { IDLE, SAVING, FAILED }

        val uiState: StateFlow<PosterUiState> =
            combine(
                repository.observePosterPath(),
                previewState,
                workingState,
            ) { posterPathResult, preview, working ->
                val posterPath = posterPathResult.dataOrNull().orEmpty()
                PosterUiState(
                    isLoading = false,
                    hasPoster = posterPath.isNotBlank() && posterFile(posterPath).exists(),
                    posterRelativePath = posterPath,
                    previewPath = preview,
                    isSaving = working == Working.SAVING,
                    failed = working == Working.FAILED,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = PosterUiState(),
            )

        fun onPhotoPicked(uri: Uri) {
            viewModelScope.launch {
                workingState.value = Working.SAVING
                val preview =
                    withContext(ioDispatcher) {
                        decodeCropAndPreview(uri)
                    }
                if (preview != null) {
                    previewState.value = preview.absolutePath
                    workingState.value = Working.IDLE
                } else {
                    workingState.value = Working.FAILED
                }
            }
        }

        fun onPickAnother() {
            previewState.value?.let { File(it).delete() }
            previewState.value = null
            workingState.value = Working.IDLE
        }

        fun onConfirmPhoto() {
            val preview = previewState.value ?: return
            viewModelScope.launch {
                workingState.value = Working.SAVING
                val ok =
                    withContext(ioDispatcher) {
                        try {
                            val dest =
                                File(context.filesDir, "$POSTER_DIR/$POSTER_FILE").apply {
                                    parentFile?.mkdirs()
                                }
                            File(preview).copyTo(dest, overwrite = true)
                            true
                        } catch (_: Exception) {
                            false
                        }
                    }
                if (ok) {
                    repository.setPosterPath("$POSTER_DIR/$POSTER_FILE")
                    previewState.update { null }
                    File(preview).delete()
                    workingState.value = Working.IDLE
                } else {
                    workingState.value = Working.FAILED
                }
            }
        }

        fun onRemovePoster() {
            viewModelScope.launch {
                withContext(ioDispatcher) {
                    File(context.filesDir, POSTER_DIR).deleteRecursively()
                }
                repository.setPosterPath("")
            }
        }

        fun onFailureDismissed() {
            workingState.value = Working.IDLE
        }

        /** Decodes a bitmap from any absolute path (for the UI layer). */
        suspend fun decodePosterFile(absolutePath: String): Bitmap? =
            withContext(ioDispatcher) {
                try {
                    BitmapFactory.decodeFile(absolutePath)
                } catch (_: Exception) {
                    null
                }
            }

        /** Absolute file for a repo-relative poster path. */
        fun posterFile(relativePath: String): File = File(context.filesDir, relativePath)

        /**
         * Decodes the picked image (downsampled), center-crops to a square,
         * scales to POSTER_SIZE, and writes a fresh JPEG preview. Re-encoding a
         * new bitmap drops all EXIF/GPS metadata from the original.
         */
        private fun decodeCropAndPreview(uri: Uri): File? {
            return try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, bounds)
                }
                var sample = 1
                val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
                while (maxSide / (sample * 2) > POSTER_SIZE * 2 && sample < MAX_SAMPLE_SIZE) {
                    sample *= 2
                }
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val decoded =
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, opts)
                    } ?: return null
                val size = minOf(decoded.width, decoded.height)
                val cropped =
                    Bitmap.createBitmap(
                        decoded,
                        (decoded.width - size) / 2,
                        (decoded.height - size) / 2,
                        size,
                        size,
                    )
                if (cropped != decoded) decoded.recycle()
                val scaled = Bitmap.createScaledBitmap(cropped, POSTER_SIZE, POSTER_SIZE, true)
                if (scaled != cropped) cropped.recycle()
                val preview = File(context.cacheDir, PREVIEW_FILE)
                FileOutputStream(preview).use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
                if (!scaled.isRecycled) scaled.recycle()
                preview
            } catch (_: Exception) {
                null
            } catch (_: OutOfMemoryError) {
                null
            }
        }
    }
