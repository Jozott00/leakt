# Leakt

[![Latest Version](https://img.shields.io/github/v/tag/Jozott00/leakt?filter=v*-DEV-*&color=006400&label=Latest%20Version)](https://github.com/Jozott00/leakt/tags)

`Leakt` is a first working prototype of a Kotlin/Native runtime library, compiler plugin, and Gradle plugin that uses LLVM LeakSanitizer (LSan) to detect native leaks inside the test process.

The project has four components:

- `core`: Kotlin Multiplatform runtime with LSan bindings and a Kotlin API.
- `compiler-plugin`: Kotlin compiler plugin that rewrites `@LeakCheck` functions to call the runtime leak-check wrapper.
- `gradle-plugin`: Gradle plugin that enables sanitizer flags and injects the runtime dependency into native test source sets.
- `test`: Sample Kotlin/Native test project that demonstrates leak detection with C interop allocations.

## Current Scope

`Leakt` currently targets Kotlin/Native **test** compilations/source sets only.  
The Gradle plugin applies compiler instrumentation and sanitizer wiring for native test code, that influences performance
negatively.

The `@LeakCheck` annotation and `withLeakCheck` API are available on all targets, but actual leak detection currently only
executes for `linuxX64Test` runs. On other targets, the API remains callable but behaves as a no-op.

## Requirements

- Kotlin `2.3.10`
- Gradle `9.2.1`
- A Linux `x86_64` host to run `linuxX64Test` with LeakSanitizer enabled
- A toolchain that can link AddressSanitizer and LeakSanitizer

## Use In A Project

Use the latest DEV version from the badge above.

Add the GitHub Packages repository in `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.pkg.github.com/Jozott00/leakt") {
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://maven.pkg.github.com/Jozott00/leakt") {
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

Apply the plugin in your multiplatform project:

```kotlin
plugins {
    kotlin("multiplatform") version "2.3.10"
    id("dev.jozott.leakt") version "<latest-version>"
}

kotlin {
    linuxX64()
}
```

Then annotate native test functions or call `withLeakCheck` manually. The plugin adds the `core` runtime to test source sets automatically.

## How It Works

`Leakt` wraps the following LeakSanitizer APIs through Kotlin/Native cinterop:

- `__lsan_do_recoverable_leak_check`
- `__lsan_disable`
- `__lsan_enable`

The runtime disables LSan by default and enables it only for the body passed to `withLeakCheck`.  
After the body finishes, it performs an in-process recoverable leak check and throws `LeakDetectedException` if leaks are reported.
That runtime leak check is only active in `linuxX64Test` execution.

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

LeakSanitizer state is process-global. Once one test leaks, later leak checks in the same test process can keep observing that
same leak, which quickly turns the output noisy and repetitive.

`FIRST_ONLY` is therefore the default: it reports the first detected leak in a process and suppresses repeated reports after
that. In practice, this means that if one test already detected a leak, a later executed test that also leaks will not fail
just because it leaked as well; the repeated report is suppressed for the rest of that process. Use `REPEAT` when you
explicitly want every leak-check scope to report again, even after an earlier failure.

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
- Outside `linuxX64Test`, `withLeakCheck` and `@LeakCheck` remain available but do not trigger leak detection yet.
