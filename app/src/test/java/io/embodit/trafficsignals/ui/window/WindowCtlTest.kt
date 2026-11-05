package io.embodit.trafficsignals.ui.window

import io.embodit.trafficsignals.ui.Screen
import org.junit.Assert.assertEquals
import org.junit.Test

/** One TabBar item lit per screen (D8): a sub-page keeps its parent's tab; settings is its own, last item (D26). */
class WindowCtlTest {
    @Test
    fun everyScreenLightsOneTab() {
        val want =
            mapOf(
                Screen.STARTING to TAB_HOME, Screen.HOME to TAB_HOME, Screen.BLOCKED to TAB_HOME,
                Screen.LESSONS to TAB_LESSONS, Screen.LESSON to TAB_LESSONS,
                Screen.LEVELS to TAB_LEVELS, Screen.INTRO to TAB_LEVELS, Screen.QUIZ to TAB_LEVELS, Screen.RESULT to TAB_LEVELS,
                Screen.REVIEW to TAB_REVIEW, Screen.SETTINGS to TAB_SETTINGS,
            )
        assertEquals(Screen.entries.toSet(), want.keys)
        for ((s, t) in want) assertEquals(s.name, t, tabOf(s))
    }
}
