package dev.jozott.leakt

import dev.jozott.leakt.lsan.__lsan_disable
import dev.jozott.leakt.lsan.__lsan_do_recoverable_leak_check
import dev.jozott.leakt.lsan.__lsan_enable
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi

@OptIn(ExperimentalForeignApi::class, NativeRuntimeApi::class)
public actual object LeakSanitizer {
    // LSan state is process-global; this flag mirrors policy handling in-process.
    private var leakAlreadyReported: Boolean = false
    private var disableDepth: Int = 0

    init {
        disable()
    }

    public actual fun disable() {
        __lsan_disable()
        disableDepth += 1
    }

    public actual fun enable() {
        check(disableDepth > 0) {
            "Unmatched call to LeakSanitizer.enable(): leak tracking is already enabled. " +
                "Do not nest leak scopes (for example, calling withLeakCheck around an @LeakCheck function), " +
                "and do not call LeakSanitizer.enable()/disable() manually out of balance."
        }
        __lsan_enable()
        disableDepth -= 1
    }

    public actual fun checkLeaks(reporting: LeakReporting): Boolean {
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

    public actual fun scope(reporting: LeakReporting, block: () -> Unit): Boolean {
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
