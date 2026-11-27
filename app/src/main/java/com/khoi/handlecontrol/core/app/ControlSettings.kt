package com.khoi.handlecontrol.core.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User settings shared by the UI and the foreground service, persisted in SharedPreferences.
 * Call [init] once (MainActivity / service onCreate) before reading the flows.
 */
object ControlSettings {

    private const val PREFS = "control_settings"
    private const val KEY_FILTER_ENABLED = "app_filter_enabled"
    private const val KEY_ALLOWED_APPS = "allowed_apps"

    /** Short-video apps selected on first launch (harmless if one is not installed). */
    val DEFAULT_ALLOWED_APPS = setOf(
        "com.zhiliaoapp.musically",   // TikTok
        "com.ss.android.ugc.trill",   // TikTok (Asia)
        "com.google.android.youtube", // YouTube
        "com.instagram.android",      // Instagram
        "com.facebook.katana"         // Facebook
    )

    private var prefs: SharedPreferences? = null

    private val _filterEnabled = MutableStateFlow(true)
    /** true = control only works inside [allowedApps]; false = everywhere. */
    val filterEnabled: StateFlow<Boolean> = _filterEnabled.asStateFlow()

    private val _allowedApps = MutableStateFlow(DEFAULT_ALLOWED_APPS)
    val allowedApps: StateFlow<Set<String>> = _allowedApps.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _filterEnabled.value = p.getBoolean(KEY_FILTER_ENABLED, true)
        _allowedApps.value = p.getStringSet(KEY_ALLOWED_APPS, null)?.toSet() ?: DEFAULT_ALLOWED_APPS
    }

    fun setFilterEnabled(enabled: Boolean) {
        _filterEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_FILTER_ENABLED, enabled)?.apply()
    }

    fun setAppAllowed(packageName: String, allowed: Boolean) {
        val updated = if (allowed) _allowedApps.value + packageName else _allowedApps.value - packageName
        _allowedApps.value = updated
        // Always store a fresh copy: SharedPreferences must not keep a reference to a mutable set.
        prefs?.edit()?.putStringSet(KEY_ALLOWED_APPS, HashSet(updated))?.apply()
    }
}
