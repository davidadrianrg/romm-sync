package com.davidadrianrg.atril.ui.viewmodel

import com.davidadrianrg.atril.data.repository.RomRepository
import com.davidadrianrg.atril.domain.model.ApiResult
import com.davidadrianrg.atril.domain.model.ErrorKind
import com.davidadrianrg.atril.domain.model.Rom
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Carga de la Biblioteca con RomM caído: estado de error, reintento y batch. */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelLoadErrorTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val failure = ApiResult.Error(ErrorKind.NETWORK, "down")
    private val repo = mockk<RomRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { repo.getDownloadedRomIds() } returns flowOf(emptySet())
        every { repo.getManualDownloadedRomIds() } returns flowOf(emptySet())
        every { repo.physicalMediaOverrides } returns MutableStateFlow(emptyMap())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun rom(id: Int, platformId: Int) = Rom(
        id = id,
        name = "rom$id",
        fileName = "rom$id.bin",
        fileSizeBytes = 1,
        platformId = platformId,
        platformSlug = "p$platformId",
        coverUrlSmall = null,
        coverUrlLarge = null,
        files = emptyList(),
        isMulti = false,
        revision = null,
        regions = emptyList(),
    )

    private fun stubFirstPage(platformId: Int, result: ApiResult<List<Rom>>) {
        coEvery { repo.fetchRoms(platformId, any(), any(), any(), any(), any()) } returns result
    }

    private fun stubWholePlatform(platformId: Int, result: ApiResult<List<Rom>>) {
        coEvery { repo.fetchAllRomsForPlatform(platformId, any()) } returns result
    }

    @Test
    fun failedSwitchDoesNotKeepPreviousPlatformRoms() = runTest(dispatcher) {
        stubFirstPage(1, ApiResult.Success(listOf(rom(10, 1))))
        stubFirstPage(2, failure)
        val vm = LibraryViewModel(repo)

        vm.selectPlatform(1, "url", "key")
        assertEquals(1, vm.roms.value.size)

        vm.selectPlatform(2, "url", "key")
        testScheduler.advanceUntilIdle()

        assertEquals(emptyList<Rom>(), vm.roms.value)
        assertNotNull(vm.error.value)
    }

    @Test
    fun errorWithEmptyListShowsNoSnackbar() = runTest(dispatcher) {
        stubFirstPage(1, failure)
        stubWholePlatform(1, failure)
        val vm = LibraryViewModel(repo)
        val events = mutableListOf<LibraryEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }

        vm.selectPlatform(1, "url", "key")
        vm.loadAllPlatformRoms()
        testScheduler.advanceUntilIdle()

        assertNotNull(vm.error.value)
        assertEquals(emptyList<LibraryEvent>(), events)
    }

    @Test
    fun failedRefreshKeepsListAndNotifiesOnce() = runTest(dispatcher) {
        stubFirstPage(1, ApiResult.Success(listOf(rom(10, 1))))
        val vm = LibraryViewModel(repo)
        val events = mutableListOf<LibraryEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }
        vm.selectPlatform(1, "url", "key")

        stubFirstPage(1, failure)
        stubWholePlatform(1, failure)
        vm.refresh("url", "key")
        testScheduler.advanceUntilIdle()

        assertEquals(1, vm.roms.value.size)
        assertEquals(1, events.filterIsInstance<LibraryEvent.Error>().size)
    }

    @Test
    fun wholePlatformFailureAfterGoodFirstPageIsNotified() = runTest(dispatcher) {
        stubFirstPage(1, ApiResult.Success(listOf(rom(10, 1))))
        stubWholePlatform(1, failure)
        val vm = LibraryViewModel(repo)
        val events = mutableListOf<LibraryEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }

        vm.selectPlatform(1, "url", "key")
        vm.loadAllPlatformRoms()
        testScheduler.advanceUntilIdle()

        assertEquals(1, events.filterIsInstance<LibraryEvent.Error>().size)
    }

    @Test
    fun retryAlsoReloadsWholePlatformForBatch() = runTest(dispatcher) {
        stubFirstPage(1, failure)
        stubWholePlatform(1, failure)
        val vm = LibraryViewModel(repo)
        backgroundScope.launch { vm.platformMissingRoms.collect {} }
        vm.selectPlatform(1, "url", "key")
        vm.loadAllPlatformRoms()
        testScheduler.advanceUntilIdle()
        assertEquals(0, vm.platformMissingRoms.value.size)

        val page = listOf(rom(10, 1))
        val whole = listOf(rom(10, 1), rom(11, 1), rom(12, 1))
        stubFirstPage(1, ApiResult.Success(page))
        stubWholePlatform(1, ApiResult.Success(whole))
        vm.refresh("url", "key")
        testScheduler.advanceUntilIdle()

        assertNull(vm.error.value)
        assertEquals(3, vm.platformMissingRoms.value.size)
    }

    @Test
    fun lateWholePlatformResponseDoesNotLeakIntoNewPlatform() = runTest(dispatcher) {
        val slowA = CompletableDeferred<ApiResult<List<Rom>>>()
        stubFirstPage(1, ApiResult.Success(listOf(rom(10, 1))))
        stubFirstPage(2, ApiResult.Success(listOf(rom(20, 2))))
        coEvery { repo.fetchAllRomsForPlatform(1, any()) } coAnswers { slowA.await() }
        stubWholePlatform(2, ApiResult.Success(listOf(rom(20, 2), rom(21, 2))))
        val vm = LibraryViewModel(repo)
        backgroundScope.launch { vm.platformMissingRoms.collect {} }

        vm.selectPlatform(1, "url", "key")
        vm.loadAllPlatformRoms()
        vm.selectPlatform(2, "url", "key")
        vm.loadAllPlatformRoms()
        slowA.complete(ApiResult.Success(listOf(rom(10, 1))))
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(20, 21), vm.platformMissingRoms.value.map { it.id })
    }
}
