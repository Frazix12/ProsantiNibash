package com.irfan.prosantinibash.feature.flats

import com.irfan.prosantinibash.data.model.Flat
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlatsViewModelTest {
    @Test fun loadingSortingFailureRetryAndConcurrentRefresh() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val pending = CompletableDeferred<Unit>()
            var fail = false
            var calls = 0
            val vm = FlatsViewModel {
                calls++
                pending.await()
                if (fail) throw java.net.SocketTimeoutException("sensitive server text")
                listOf(Flat("b", 2, "2B", 0), Flat("a", 1, "1A", 0))
            }
            vm.refresh()
            vm.refresh()
            runCurrent()
            assertTrue(vm.uiState.value.loading)
            assertEquals(1, calls)
            pending.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf("1A", "2B"), vm.uiState.value.flats.map { it.flatNumber })
            fail = true
            vm.refresh()
            advanceUntilIdle()
            assertNotNull(vm.uiState.value.error)
            assertFalse(vm.uiState.value.error!!.contains("sensitive"))
            assertEquals(2, vm.uiState.value.flats.size)
            fail = false
            vm.refresh()
            advanceUntilIdle()
            assertNull(vm.uiState.value.error)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun cancelledAndInvalidRequestsAllowRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var outcome = 0
            val vm = FlatsViewModel {
                if (outcome == 0) throw CancellationException("Cancelled")
                if (outcome == 1) throw IllegalArgumentException("private payload")
                emptyList()
            }
            vm.refresh()
            advanceUntilIdle()
            assertFalse(vm.uiState.value.loading)
            assertNull(vm.uiState.value.error)
            outcome = 1
            vm.refresh()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.error!!.contains("building configuration"))
            assertFalse(vm.uiState.value.error!!.contains("private"))
            outcome = 2
            vm.refresh()
            advanceUntilIdle()
            assertFalse(vm.uiState.value.loading)
            assertNull(vm.uiState.value.error)
        } finally { Dispatchers.resetMain() }
    }

}
