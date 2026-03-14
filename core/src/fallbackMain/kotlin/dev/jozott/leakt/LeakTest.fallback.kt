package dev.jozott.leakt

public actual fun withLeakCheck(reporting: dev.jozott.leakt.LeakReporting, block: () -> Unit) {
    // this is a no-op on non-linuxX64 platforms
    block()
}
