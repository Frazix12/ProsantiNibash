package com.irfan.prosantinibash.feature.flats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.irfan.prosantinibash.data.model.Flat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class FlatsUiState(val flats: List<Flat> = emptyList(), val loading: Boolean = false, val error: String? = null)

/** Refresh is explicit and single-flight; failures retain the last confirmed cloud data. */
class FlatsViewModel(private val loadFlats: suspend () -> List<Flat>) : ViewModel() {
    private val mutableState = MutableStateFlow(FlatsUiState())
    val uiState = mutableState.asStateFlow()
    private var request: Job? = null
    fun refresh() {
        if (request?.isActive == true) return
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        request = viewModelScope.launch {
            try {
                val flats = loadFlats().sortedWith(compareBy(Flat::floor, Flat::flatNumber))
                mutableState.value = FlatsUiState(flats)
            } catch (e: CancellationException) {
                mutableState.value = mutableState.value.copy(loading = false)
                throw e
            } catch (_: IllegalArgumentException) {
                mutableState.value = mutableState.value.copy(loading = false,
                    error = "Cloud flat data is invalid. Check the building configuration and try again.")
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(loading = false,
                    error = "Flats could not be loaded. Check your connection and try again.")
            }
        }
    }
}
