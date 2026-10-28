package com.nexmusic.app.island

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.view.accessibility.AccessibilityEvent

/** Hosts [DynamicIsland]. It reads no screen content; it only needs an accessibility window above the status bar. */
class IslandService : AccessibilityService() {
    override fun onServiceConnected() = DynamicIsland.attach(this)

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        DynamicIsland.relayout()
    }

    override fun onDestroy() {
        DynamicIsland.detach()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit
}
