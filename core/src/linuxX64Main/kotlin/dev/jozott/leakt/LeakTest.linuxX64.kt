package dev.jozott.leakt

public actual fun withLeakCheck(reporting: LeakReporting, block: () -> Unit) {
    if (LeakSanitizer.scope(reporting = reporting, block = block)) {
        throw LeakDetectedException()
    }
}