package com.khoi.handlecontrol.core.app

/**
 * Decides whether hand control may act right now, based on the app in the foreground.
 * Pure logic, no Android types, so it is unit-tested.
 */
object AppGate {

    /**
     * [ACTIVE]: recognise and execute. [TEST]: inside Hand Control itself, recognise and show
     * the gesture but do not execute it (Back / swipes would leave or scroll this screen).
     * Others: paused, with the reason shown in the UI and the notification.
     */
    enum class Decision { ACTIVE, TEST, NOT_SELECTED_APP, UNKNOWN_APP }

    /** Frames are analysed (AI runs) in these states. */
    val Decision.recognises: Boolean get() = this == Decision.ACTIVE || this == Decision.TEST

    /**
     * @param filterEnabled "only work inside the selected apps"; when false, control works everywhere.
     * @param foregroundPackage app currently on screen, or null when it is not known
     *        (Accessibility off, or no app switch seen yet).
     * @param allowedPackages apps the user selected.
     * @param ownAppVisible Hand Control's own screen is in front (known even without Accessibility).
     */
    fun decide(
        filterEnabled: Boolean,
        foregroundPackage: String?,
        allowedPackages: Set<String>,
        ownAppVisible: Boolean = false
    ): Decision = when {
        ownAppVisible -> Decision.TEST
        !filterEnabled -> Decision.ACTIVE
        foregroundPackage == null -> Decision.UNKNOWN_APP
        foregroundPackage in allowedPackages -> Decision.ACTIVE
        else -> Decision.NOT_SELECTED_APP
    }

    /**
     * Whether a window event from [packageName] means the user switched app.
     * System UI (notification shade, volume panel) and the keyboard draw on top of the current
     * app without leaving it, so they must not change the foreground app.
     */
    fun isTransientPackage(packageName: String, inputMethodPackages: Set<String>): Boolean =
        packageName in TRANSIENT_PACKAGES || packageName in inputMethodPackages

    private val TRANSIENT_PACKAGES = setOf(
        "com.android.systemui",
        "android"
    )
}
