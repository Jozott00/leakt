# Leakt

`Leakt` is a first working prototype of a Kotlin/Native runtime library plus Gradle plugin that uses LLVM LeakSanitizer (LSan) to detect native leaks inside the test process.

The project has three components:

- `core`: Kotlin Multiplatform runtime with LSan bindings and a Kotlin API.
- `gradle-plugin`: Gradle plugin that enables sanitizer flags and injects the runtime dependency into native test source sets.
- `test`: Sample Kotlin/Native test project that demonstrates leak detection with C interop allocations.

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

The runtime disables LSan by default and only enables it around the code passed to `withLeakCheck`. After the block finishes, it performs an in-process recoverable leak check and throws an `IllegalStateException` when LSan reports leaked native memory.

## Example Usage

```kotlin
@Test
@LeakCheck
fun detectsLeakedNativeAllocation() {
    withLeakCheck {
        nativeHeap.alloc<IntVar>()
    }
}
```

`@LeakCheck` supports reporting policy:

```kotlin
@LeakCheck(reporting = LeakReporting.REPEAT)
@Test
fun alwaysReportLeaks() { /* ... */ }
```

`FIRST_ONLY` (default) suppresses repeated reports after the first detected leak in the same process.

The included test keeps the build green by asserting that the helper throws when a leak is detected. If you want to see a hard test failure instead, remove the `assertFailsWith` wrapper in the test.

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
- The plugin currently enables AddressSanitizer/LeakSanitizer with straightforward compiler and linker flags; it is intended as a readable prototype rather than a production-hardened integration.
- On the current Apple toolchain, the sanitizer runtime links but does not surface recoverable leak checks reliably. The macOS test therefore keeps the build runnable and reserves the full per-test leak assertion for Linux targets.
- Windows is intentionally out of scope for the prototype.
