package dev.jozott.leaktest

import dev.jozott.leaktest.lsan.__lsan_disable
import dev.jozott.leaktest.lsan.__lsan_do_recoverable_leak_check
import dev.jozott.leaktest.lsan.__lsan_enable
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi

@OptIn(ExperimentalForeignApi::class, NativeRuntimeApi::class)
public actual object LeakSanitizer {
    init {
        disable()
    }

    public actual fun disable() {
        __lsan_disable()
    }

    public actual fun enable() {
        __lsan_enable()
    }

    public actual fun checkLeaks(): Boolean {
        collect()
        return __lsan_do_recoverable_leak_check() != 0
    }

    public actual fun scope(name: String?, block: () -> Unit) {
        enable()
        try {
            block()
        } finally {
            disable()
        }

        if (checkLeaks()) {
            val testName = name ?: "anonymous test"
            error("Memory leak detected in $testName")
        }
    }

    private fun collect() {
        GC.collect()
    }
}
