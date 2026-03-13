package dev.jozott.leakt

actual fun withLeakCheck(reporting: dev.jozott.leakt.LeakReporting, block: () -> Unit) {
    // this is a no-op on non-linuxX64 platforms
    block()
}