package com.dianming.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dianming.app.DianMingApp
import com.dianming.app.data.ClassInfo
import com.dianming.app.data.Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val classes: List<ClassInfo> = emptyList()
)

class HomeViewModel(private val repo: Repository) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.tick.collect { load() }
        }
    }

    private suspend fun load() {
        val list = repo.listClasses()
        _state.update { it.copy(loading = false, classes = list) }
    }

    fun deleteClass(classId: String) {
        viewModelScope.launch { repo.deleteClass(classId) }
    }

    companion object {
        val factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DianMingApp
                HomeViewModel(app.repository)
            }
        }
    }
}
