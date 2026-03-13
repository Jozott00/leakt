# Leakt

`Leakt` is a first working prototype of a Kotlin/Native runtime library plus Gradle plugin that uses LLVM LeakSanitizer (LSan) to detect native leaks inside the test process.

The project has three components:

- `core`: Kotlin Multiplatform runtime with LSan bindings and a Kotlin API.
- `gradle-plugin`: Gradle plugin that enables sanitizer flags and injects the runtime dependency into native test source sets.
- `test`: Sample Kotlin/Native test project that demonstrates leak detection with C interop allocations.

## Current Scope

`Leakt` currently targets Kotlin/Native **test** compilations/source sets only.  
The Gradle plugin applies compiler instrumentation and sanitizer wiring for native test code, that influences performance
negatively.

## Requirements

- Kotlin `2.3.10`
- Gradle `9.2.1`
- Kotlin/Native host support on `linuxX64`, `macosX64`, or `macosArm64`
- A toolchain that can link AddressSanitizer and LeakSanitizer

## How It Works

`Leakt` wraps the following LeakSanitizer APIs through Kotlin/Native cinterop:

- `__lsan_do_recoverable_leak_check`
- `__lsan_disable`
- `__lsan_enable`

The runtime disables LSan by default and enables it only for the body passed to `withLeakCheck`.  
After the body finishes, it performs an in-process recoverable leak check and throws `LeakDetectedException` if leaks are reported.

The compiler plugin rewrites `@LeakCheck` functions to call `withLeakCheck(reporting) { ... }`.

## Example Usage

```kotlin
@Test
@LeakCheck
fun detectsLeakedNativeAllocation() {
    nativeHeap.alloc<IntVar>()
}
```

Manual usage without annotation:

```kotlin
withLeakCheck {
    nativeHeap.alloc<IntVar>()
}
```

`@LeakCheck` and `withLeakCheck` both support reporting policy:

```kotlin
withLeakCheck(reporting = LeakReporting.REPEAT) {
    /* ... */
}
```

`FIRST_ONLY` (default) suppresses repeated reports after the first detected leak in the same process.

## Important Constraint

Nesting leak scopes is not allowed:

- Do not call `withLeakCheck` inside another `withLeakCheck`.
- Do not call `withLeakCheck` around an `@LeakCheck` function.
- Do not call an `@LeakCheck` function from inside another `@LeakCheck` function.

If this happens, runtime state can become inconsistent and leak checking will fail fast.

## Running The Prototype

```bash
./gradlew :test:check
```

Useful follow-up commands:

```bash
./gradlew :test:allTests
./gradlew :core:publishToMavenLocal
```

## Limitations

- Once a test leaks native memory, later in-process leak checks may still observe that leak until the test process exits.
- Leak reports are process-global; there is no runtime reset for already observed leaks.
- On non-`linuxX64` fallback targets, `withLeakCheck` is currently a no-op.
