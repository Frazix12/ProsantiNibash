package com.irfan.prosantinibash.feature.book

import androidx.lifecycle.SavedStateHandle
import com.irfan.prosantinibash.data.model.Book
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookViewModelTest {
    @Test fun confirmedCacheAppearsBeforeNetworkAndSavePublishesSingleCloudResult() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val cached = Book(building = com.irfan.prosantinibash.data.model.Building(name = "Cached"))
            val confirmed = cached.copy(building = cached.building.copy(name = "Cloud confirmed"))
            val network = CompletableDeferred<Book>()
            val commit = CompletableDeferred<Book>()
            var reads = 0
            val model = BookViewModel({ reads++; network.await() }, { _, _, _ -> error("Legacy save should not run") },
                cachedBook = { cached }, commitBook = { _, _, _ -> commit.await() })
            model.refresh(); runCurrent()
            assertEquals(cached, model.uiState.value.book)
            assertTrue(model.uiState.value.loaded)
            assertTrue(model.uiState.value.stale)
            model.save("building_save", buildJsonObject { put("name", "Cloud confirmed") }, "stable")
            runCurrent()
            assertTrue(model.uiState.value.saving)
            assertEquals(cached, model.uiState.value.book)
            assertEquals("stable", model.uiState.value.pendingRequestId)
            commit.complete(confirmed); advanceUntilIdle()
            assertEquals(confirmed, model.uiState.value.book)
            assertFalse(model.uiState.value.stale)
            assertNull(model.uiState.value.pendingRequestId)
            assertEquals(1, reads)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun timeoutRetryKeepsOriginalAmountAcrossProcessRestoreAndBlocksConcurrentSave() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val saved=SavedStateHandle();val requests=mutableListOf<JsonObject>()
            val original=buildJsonObject { put("amount_paisa",100L) }
            val edited=buildJsonObject { put("amount_paisa",999L) }
            val first=BookViewModel({ Book() },{ _,payload,_ -> requests+=payload;throw java.net.SocketTimeoutException() },saved)
            first.refresh();advanceUntilIdle();first.save("payment_save",original,"stable")
            first.save("payment_save",original,"stable");advanceUntilIdle()
            assertEquals(1,requests.size);assertEquals("stable",first.uiState.value.pendingRequestId)
            val restored=BookViewModel({ Book() },{ _,payload,_ -> requests+=payload },saved)
            restored.refresh();advanceUntilIdle();restored.save("payment_save",edited,"other");advanceUntilIdle()
            assertEquals(1,requests.size)
            restored.save("payment_save",edited,"stable");advanceUntilIdle()
            assertEquals(listOf(original,original),requests);assertNull(restored.uiState.value.pendingRequestId)
            assertEquals(1,restored.uiState.value.savedSequence)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun delayedLoadAndFailedRefreshKeepTruthfulState() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var fail = false
            val book = Book()
            val model = BookViewModel({ delay(1000); if (fail) error("offline") else book }, { _, _, _ -> })
            model.refresh(); runCurrent()
            assertTrue(model.uiState.value.loading); assertFalse(model.uiState.value.loaded)
            advanceUntilIdle(); assertTrue(model.uiState.value.loaded)
            fail = true; model.refresh(); advanceUntilIdle()
            assertSame(book, model.uiState.value.book); assertTrue(model.uiState.value.loaded)
            assertFalse(model.uiState.value.loading); assertNotNull(model.uiState.value.error)
            val saveModel = BookViewModel({ Book() }, { _, _, _ -> throw CancellationException("request interrupted") })
            saveModel.refresh(); advanceUntilIdle()
            saveModel.save("payment_save", buildJsonObject { put("amount_paisa", 100L) }, "original")
            advanceUntilIdle()
            assertFalse(saveModel.uiState.value.saving); assertNotNull(saveModel.uiState.value.error)
            assertEquals("original", saveModel.uiState.value.pendingRequestId)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun operationCancellationShowsRetryWhenViewModelIsStillActive() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val model = BookViewModel({ throw CancellationException("request interrupted") }, { _, _, _ -> })
            model.refresh(); advanceUntilIdle()
            assertFalse(model.uiState.value.loading); assertNotNull(model.uiState.value.error)
            val saveModel = BookViewModel({ Book() }, { _, _, _ -> throw CancellationException("request interrupted") })
            saveModel.refresh(); advanceUntilIdle()
            saveModel.save("payment_save", buildJsonObject { put("amount_paisa", 100L) }, "original")
            advanceUntilIdle()
            assertFalse(saveModel.uiState.value.saving); assertNotNull(saveModel.uiState.value.error)
            assertEquals("original", saveModel.uiState.value.pendingRequestId)
        } finally { Dispatchers.resetMain() }
    }
}
