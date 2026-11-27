package com.khoi.handlecontrol.core.action

import com.khoi.handlecontrol.core.gesture.Gesture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionMapperTest {

    @Test
    fun default_mapping_matches_plan() {
        val mapper = ActionMapper()
        assertEquals(ControlAction.NEXT, mapper.map(Gesture.SWIPE_UP))
        assertEquals(ControlAction.PREVIOUS, mapper.map(Gesture.SWIPE_DOWN))
        assertEquals(ControlAction.SWIPE_LEFT, mapper.map(Gesture.SWIPE_LEFT))
        assertEquals(ControlAction.SWIPE_RIGHT, mapper.map(Gesture.SWIPE_RIGHT))
        assertEquals(ControlAction.DOUBLE_TAP, mapper.map(Gesture.THUMB_UP))
        assertEquals(ControlAction.BACK, mapper.map(Gesture.THUMB_LEFT))
        assertEquals(ControlAction.PLAY_PAUSE, mapper.map(Gesture.FIST))
        assertEquals(ControlAction.VOLUME_UP, mapper.map(Gesture.POINT_UP))
        assertEquals(ControlAction.VOLUME_DOWN, mapper.map(Gesture.POINT_DOWN))
    }

    @Test
    fun remapping_returns_new_mapper() {
        val original = ActionMapper()
        val remapped = original.with(Gesture.FIST, ControlAction.BACK)
        assertEquals(ControlAction.BACK, remapped.map(Gesture.FIST))
        assertEquals(ControlAction.PLAY_PAUSE, original.map(Gesture.FIST))
    }

    @Test
    fun unmapped_gesture_is_none() {
        val mapper = ActionMapper(mapOf(Gesture.SWIPE_LEFT to ControlAction.BACK))
        assertEquals(ControlAction.NONE, mapper.map(Gesture.POINT_UP))
    }

    @Test
    fun volume_does_not_need_accessibility() {
        assertFalse(ControlAction.VOLUME_UP.needsAccessibility)
        assertFalse(ControlAction.MEDIA_PLAY_PAUSE.needsAccessibility)
        assertTrue(ControlAction.NEXT.needsAccessibility)
    }
}
