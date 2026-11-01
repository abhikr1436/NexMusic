package com.nexmusic.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {
    @Test
    fun comparesNumberByNumber() {
        assertTrue(AppUpdater.isNewer("1.1.2", "1.1.1"))
        assertTrue(AppUpdater.isNewer("1.2.0", "1.1.10"))
        assertTrue(AppUpdater.isNewer("1.1.10", "1.1.9"))
        assertTrue(AppUpdater.isNewer("2.0", "1.9.9"))
        assertFalse(AppUpdater.isNewer("1.1.1", "1.1.1"))
        assertFalse(AppUpdater.isNewer("1.1.0", "1.1.1"))
        assertFalse(AppUpdater.isNewer("1.1", "1.1.0"))
        assertFalse(AppUpdater.isNewer("1.1.1-beta", "1.1.1"))
    }
}
