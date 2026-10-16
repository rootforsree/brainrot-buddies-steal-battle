package com.sreecharan.brainrot_buddies_steal_battle.ui.poster

import android.content.Context
import app.cash.turbine.test
import com.sreecharan.brainrot_buddies_steal_battle.fakes.FakeGameRepository
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PosterViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeGameRepository
    private lateinit var context: Context
    private lateinit var filesDir: File

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeGameRepository()
        filesDir = Files.createTempDirectory("poster-test").toFile()
        context = mockk {
            every { getFilesDir() } returns filesDir
            every { getCacheDir() } returns filesDir
        }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        filesDir.deleteRecursively()
    }

    private fun viewModel() = PosterViewModel(repository, context, testDispatcher)

    @Test
    fun `starts with no poster`() = runTest(testDispatcher) {
        val vm = viewModel()

        vm.uiState.test {
            awaitItem() // loading
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertFalse(state.hasPoster)
            assertEquals("", state.posterRelativePath)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `existing poster file surfaces hasPoster`() = runTest(testDispatcher) {
        val posterFile = File(filesDir, "posters/poster.jpg")
        posterFile.parentFile!!.mkdirs()
        posterFile.writeBytes(ByteArray(8))
        repository.posterPathState.value = "posters/poster.jpg"
        val vm = viewModel()

        vm.uiState.test {
            awaitItem() // loading
            val state = awaitItem()
            assertTrue(state.hasPoster)
            assertEquals("posters/poster.jpg", state.posterRelativePath)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stored path without a file is not a poster`() = runTest(testDispatcher) {
        repository.posterPathState.value = "posters/poster.jpg"
        val vm = viewModel()

        vm.uiState.test {
            awaitItem() // loading
            assertFalse(awaitItem().hasPoster)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removing the poster deletes the file and clears the path`() =
        runTest(testDispatcher) {
            val posterFile = File(filesDir, "posters/poster.jpg")
            posterFile.parentFile!!.mkdirs()
            posterFile.writeBytes(ByteArray(8))
            repository.posterPathState.value = "posters/poster.jpg"
            val vm = viewModel()

            vm.onRemovePoster()
            advanceUntilIdle()

            assertFalse(posterFile.exists())
            assertEquals("", repository.posterPathState.value)
            vm.uiState.test {
                awaitItem() // loading (or current)
                val state = awaitItem()
                assertFalse(state.hasPoster)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `failure flag can be dismissed`() = runTest(testDispatcher) {
        val vm = viewModel()
        vm.onFailureDismissed()
        advanceUntilIdle()

        vm.uiState.test {
            awaitItem() // loading
            assertFalse(awaitItem().failed)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
