package dev.jozott.leakt

/**
 * Low-level LeakSanitizer runtime bridge.
 *
 * Use [withLeakCheck] for the high-level API unless fine-grained control is required.
 */
public expect object LeakSanitizer {
    /**
     * Disables leak tracking for the current thread.
     */
    public fun disable()

    /**
     * Enables leak tracking for the current thread.
     */
    public fun enable()

    /**
     * Performs a recoverable leak check.
     *
     * Returns `true` if leaks should be reported under the given [reporting] policy.
     */
    public fun checkLeaks(reporting: LeakReporting = LeakReporting.FIRST_ONLY): Boolean

    /**
     * Runs [block] with leak tracking enabled, then performs [checkLeaks].
     *
     * Nesting calls to [scope] is not allowed.
     *
     * Returns `true` if leaks should be reported.
     */
    public fun scope(reporting: LeakReporting = LeakReporting.FIRST_ONLY, block: () -> Unit): Boolean
}
