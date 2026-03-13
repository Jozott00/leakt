package dev.jozott.leakt

/**
 * Thrown when a leak check determines that memory leaks are present.
 */
public class LeakDetectedException : RuntimeException("Memory leak was detected")

/**
 * Executes [block] under leak checking and throws [LeakDetectedException] when leaks are detected.
 *
 * This is the recommended runtime entry point for manual leak checks.
 *
 * Nesting leak scopes is not allowed. In particular, do not call [withLeakCheck] from
 * inside another [withLeakCheck] block and do not call it around [LeakCheck]-annotated functions.
 */
public fun withLeakCheck(reporting: LeakReporting = LeakReporting.FIRST_ONLY, block: () -> Unit) {
    if (LeakSanitizer.scope(reporting = reporting, block = block)) {
        throw LeakDetectedException()
    }
}
