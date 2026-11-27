package com.khoi.handlecontrol.core.app

import com.khoi.handlecontrol.core.app.AppGate.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppGateTest {

    private val tiktok = "com.zhiliaoapp.musically"
    private val launcher = "com.sec.android.app.launcher"
    private val allowed = setOf(tiktok, "com.google.android.youtube")

    @Test
    fun selected_app_is_active() {
        assertEquals(Decision.ACTIVE, AppGate.decide(true, tiktok, allowed))
    }

    @Test
    fun home_screen_is_paused() {
        assertEquals(Decision.NOT_SELECTED_APP, AppGate.decide(true, launcher, allowed))
    }

    @Test
    fun other_app_is_paused() {
        assertEquals(Decision.NOT_SELECTED_APP, AppGate.decide(true, "com.android.chrome", allowed))
    }

    @Test
    fun unknown_foreground_is_paused() {
        // Accessibility off: we cannot tell which app is open, so we do not act.
        assertEquals(Decision.UNKNOWN_APP, AppGate.decide(true, null, allowed))
    }

    @Test
    fun no_app_selected_pauses_everywhere() {
        assertEquals(Decision.NOT_SELECTED_APP, AppGate.decide(true, tiktok, emptySet()))
    }

    @Test
    fun filter_off_works_everywhere() {
        assertEquals(Decision.ACTIVE, AppGate.decide(false, launcher, allowed))
        assertEquals(Decision.ACTIVE, AppGate.decide(false, null, allowed))
    }

    @Test
    fun own_app_is_test_mode() {
        val own = "com.khoi.handlecontrol"
        assertEquals(Decision.TEST, AppGate.decide(true, own, allowed, ownAppVisible = true))
        // Known from MainActivity even when Accessibility is off (foreground unknown).
        assertEquals(Decision.TEST, AppGate.decide(true, null, allowed, ownAppVisible = true))
        // Also with the filter off: never execute Back / swipes on Hand Control's own screen.
        assertEquals(Decision.TEST, AppGate.decide(false, own, allowed, ownAppVisible = true))
    }

    @Test
    fun test_mode_recognises_but_paused_does_not() {
        with(AppGate) {
            assertTrue(Decision.ACTIVE.recognises)
            assertTrue(Decision.TEST.recognises)
            assertFalse(Decision.NOT_SELECTED_APP.recognises)
            assertFalse(Decision.UNKNOWN_APP.recognises)
        }
    }

    @Test
    fun system_ui_and_keyboard_do_not_count_as_app_switch() {
        val keyboards = setOf("com.google.android.inputmethod.latin")
        assertTrue(AppGate.isTransientPackage("com.android.systemui", keyboards))
        assertTrue(AppGate.isTransientPackage("com.google.android.inputmethod.latin", keyboards))
        assertFalse(AppGate.isTransientPackage(tiktok, keyboards))
        assertFalse(AppGate.isTransientPackage(launcher, keyboards))
    }
}
