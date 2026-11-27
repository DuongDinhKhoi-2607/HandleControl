package com.khoi.handlecontrol.core.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether Hand Control's own screen is in front. Set from MainActivity onResume / onPause,
 * so test mode works even before Accessibility is turned on.
 */
object OwnAppVisibility {
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    fun set(visible: Boolean) {
        _visible.value = visible
    }
}
