package com.v2ray.ang.ui.hotspot

import android.app.Application
import com.v2ray.ang.hotspot.ShizziController
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HotspotViewModel(application: Application) : BaseViewModel(application) {

    private val _state = MutableStateFlow(ShizziController.state.value)
    val state: StateFlow<ShizziController.State> = _state.asStateFlow()

    private val _tokenMissing = MutableStateFlow(false)
    val tokenMissing: StateFlow<Boolean> = _tokenMissing.asStateFlow()

    init {
        ShizziController.attach(application)
        viewModelScope.launch {
            ShizziController.state.collect { _state.value = it }
        }
    }

    fun saveToken(token: String) {
        if (token.isBlank()) {
            _tokenMissing.value = true
            return
        }
        _tokenMissing.value = false
        ShizziController.saveToken(token)
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled) ShizziController.start() else ShizziController.stop()
    }

    fun refresh() {
        ShizziController.refresh()
    }

    override fun onCleared() {
        ShizziController.detach()
        super.onCleared()
    }
}
