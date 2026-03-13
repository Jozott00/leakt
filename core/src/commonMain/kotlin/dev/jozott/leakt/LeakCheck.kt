package dev.jozott.leakt

/**
 * Controls how repeated leak reports are handled within the same process.
 */
public enum class LeakReporting {
    /**
     * Report only until the first leak has been observed in this process.
     */
    FIRST_ONLY,

    /**
     * Always run leak checks and report leaks, even after previous detections.
     */
    REPEAT,
}

/**
 * Marks a function for leak-check wrapping by the compiler plugin.
 *
 * The generated wrapper executes the function body with leak tracking enabled and
 * performs a recoverable leak check afterwards.
 *
 * Nesting leak scopes is not allowed. Do not call [withLeakCheck] around a function
 * annotated with [LeakCheck], and do not call one [LeakCheck]-annotated function from
 * inside another.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
public annotation class LeakCheck(
    /**
     * Reporting policy to apply for this annotated function.
     */
    val reporting: LeakReporting = LeakReporting.FIRST_ONLY,
)
