package dev.jozott.leakt.test

import dev.jozott.leakt.LeakCheck
import dev.jozott.leakt.LeakDetectedException
import dev.jozott.leakt.LeakReporting
import dev.jozott.leakt.test.ffileaks.leakt_native_alloc
import dev.jozott.leakt.test.ffileaks.leakt_native_free
import dev.jozott.leakt.test.utils.captureNewLeaks
import dev.jozott.leakt.withLeakCheck
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.sizeOf
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.OsFamily
import kotlin.native.Platform
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for leak detection behavior on native targets.
 *
 * Notes:
 * - LeakSanitizer state is process-global.
 * - `captureNewLeaks` computes report deltas against previous captures in the same process.
 */
@OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
class LeakTest {
    // Distinct leak sizes to make report assertions unambiguous.
    private val nativeLeakBytes = 333
    private val ffiLeakBytes = 444

    @Test
    @LeakCheck
    fun `given nativeHeap allocation when freed then leak check passes`() {
        val value = nativeHeap.alloc<IntVar>()
        nativeHeap.free(value.rawPtr)
    }

    @Test
    fun `given ffi allocation when freed then leak check passes`() {
        withLeakCheck {
            val raw = leakt_native_alloc(sizeOf<IntVar>().convert()) ?: error("leakt_native_alloc failed")
            leakt_native_free(raw)
        }
    }

    @LeakCheck(reporting = LeakReporting.REPEAT)
    fun detectsLeakedNativeAllocationWithAnnotation() {
        nativeHeap.allocArray<ByteVar>(nativeLeakBytes)
    }

    @Test
    fun `given leaked native allocation when leak check runs then new leak report is captured`() {
        val report = captureNewLeaks {
            detectsLeakedNativeAllocationWithAnnotation()
        }
        assertNotNull(report, "Expected a leak report")
        assertTrue(report.entries.isNotEmpty(), "Expected at least one leak entry")
        assertTrue(
            report.entries.any { it.bytes == nativeLeakBytes.toLong() },
            "Expected leak report to contain $nativeLeakBytes leaked bytes"
        )
    }

    @LeakCheck(reporting = LeakReporting.REPEAT)
    fun detectsLeakedFfiNativeAllocationWithAnnotation() {
        leakt_native_alloc(ffiLeakBytes.convert()) ?: error("leakt_native_alloc failed")
    }

    @Test
    fun `given leaked ffi allocation when leak check runs then new leak report is captured`() {
        val report = captureNewLeaks {
            detectsLeakedFfiNativeAllocationWithAnnotation()
        }
        assertNotNull(report, "Expected a leak report")
        assertTrue(report.entries.isNotEmpty(), "Expected at least one leak entry")
        assertTrue(
            report.entries.any { it.bytes == ffiLeakBytes.toLong() },
            "Expected leak report to contain $ffiLeakBytes leaked bytes"
        )
    }

    @LeakCheck(reporting = LeakReporting.REPEAT)
    fun failsEvenWithoutAllocation() {
        // Intentionally empty. If a prior leak exists, LSan can still report it again.
    }

    @Test
    fun `given prior leak when checked without new allocations then leak report still appears`() {
        captureNewLeaks {
            detectsLeakedNativeAllocationWithAnnotation()
        }

        assertFailsWith<LeakDetectedException> {
            // we use the public leak check that does not diff between leaks
            failsEvenWithoutAllocation()
        }
    }
}
