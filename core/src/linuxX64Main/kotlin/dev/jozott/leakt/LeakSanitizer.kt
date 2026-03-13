package dev.jozott.leakt

import dev.jozott.leakt.lsan.__lsan_disable
import dev.jozott.leakt.lsan.__lsan_do_recoverable_leak_check
import dev.jozott.leakt.lsan.__lsan_enable
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi

/**
 * Low-level LeakSanitizer runtime bridge.
 *
 * Use [withLeakCheck] for the high-level API unless fine-grained control is required.
 */
@OptIn(ExperimentalForeignApi::class, NativeRuntimeApi::class)
public object LeakSanitizer {
    // LSan state is process-global; this flag mirrors policy handling in-process.
    private var leakAlreadyReported: Boolean = false
    private var disableDepth: Int = 0

    init {
        disable()
    }

    /**
     * Disables leak tracking for the current thread.
     */
    public fun disable() {
        __lsan_disable()
        disableDepth += 1
    }

    /**
     * Enables leak tracking for the current thread.
     */
    public fun enable() {
        check(disableDepth > 0) {
            "Unmatched call to LeakSanitizer.enable(): leak tracking is already enabled. " +
                "Do not nest leak scopes (for example, calling withLeakCheck around an @LeakCheck function), " +
                "and do not call LeakSanitizer.enable()/disable() manually out of balance."
        }
        __lsan_enable()
        disableDepth -= 1
    }

    /**
     * Performs a recoverable leak check.
     *
     * Returns `true` if leaks should be reported under the given [reporting] policy.
     */
    public fun checkLeaks(reporting: LeakReporting): Boolean {
        if (reporting == LeakReporting.FIRST_ONLY && leakAlreadyReported) {
            return false
        }

        collect()
        val leakDetected = __lsan_do_recoverable_leak_check() != 0
        if (leakDetected) {
            leakAlreadyReported = true
        }
        return leakDetected
    }

    /**
     * Runs [block] with leak tracking enabled, then performs [checkLeaks].
     *
     * Nesting calls to [scope] is not allowed.
     *
     * Returns `true` if leaks should be reported.
     */
    public fun scope(reporting: LeakReporting, block: () -> Unit): Boolean {
        val toggled = disableDepth > 0
        if (toggled) enable()
        try {
            block()
        } finally {
            if (toggled) disable()
        }

        return checkLeaks(reporting)
    }

    private fun collect() {
        GC.collect()
    }
}
