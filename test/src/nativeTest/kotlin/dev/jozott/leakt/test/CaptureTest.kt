package dev.jozott.leakt.test

import dev.jozott.leakt.LeakDetectedException
import dev.jozott.leakt.LeakReporting
import dev.jozott.leakt.test.utils.CaptureOut
import dev.jozott.leakt.test.utils.LeakReportParser
import dev.jozott.leakt.test.utils.captureNewLeaks
import dev.jozott.leakt.test.utils.captureOutput
import dev.jozott.leakt.withLeakCheck
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.nativeHeap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class CaptureTest {
    private fun firstLeakingScope() {
        withLeakCheck(reporting = LeakReporting.REPEAT) {
            nativeHeap.allocArray<ByteVar>(111)
        }
    }

    private fun secondLeakingScope() {
        withLeakCheck(reporting = LeakReporting.REPEAT) {
            nativeHeap.allocArray<ByteVar>(222)
        }
    }

    @Test
    fun testStdOutCapture() {
        val result = captureOutput(CaptureOut.StdOut) {
            println("This should go to stdout")
        }
        assertEquals("This should go to stdout", result.first.trim(), "Output should be captured")
    }

    @Test
    fun testStdErrCapture() {
        val output = captureOutput(CaptureOut.StdErr) {
            withLeakCheck {
                nativeHeap.alloc<IntVar>()
            }
        }

        assertIs<LeakDetectedException>(output.second, "Expected LeakDetectedException")
        val reports = LeakReportParser.parse(output.first)
        assertTrue(reports.isNotEmpty(), "Expected at least one leak report in stderr output")

        val firstReport = reports.first()
        assertTrue(firstReport.entries.isNotEmpty(), "Expected at least one leak entry")

        val firstEntry = firstReport.entries.first()
        assertTrue(firstEntry.bytes > 0, "Expected positive leaked byte count")
        assertTrue(firstEntry.objects > 0, "Expected positive leaked object count")
        assertTrue(firstEntry.frames.isNotEmpty(), "Expected at least one stack frame")

        val summary = assertNotNull(firstReport.summary, "Expected leak summary line")
        assertTrue(summary.bytesLeaked > 0, "Expected positive leaked summary byte count")
        assertTrue(summary.allocations > 0, "Expected positive allocation summary count")
    }

    @Test
    fun testStdErrCaptureParsesSecondLeakReport() {
        val first = captureNewLeaks {
            firstLeakingScope()
        }

        val second = captureNewLeaks {
            println("No new leaks here")
        }

        val third = captureNewLeaks {
            secondLeakingScope()
        }

        val firstEntries = assertNotNull(first, "Expected first capture to contain new leak entries").entries
        assertTrue(firstEntries.isNotEmpty(), "Expected first capture to return at least one new entry")

        assertNull(second, "Expected second capture to return null since no new leaks were detected")

        val thirdEntries = assertNotNull(third, "Expected second capture to contain new leak entries").entries
        assertTrue(thirdEntries.size == 1, "Expected second capture to return only the newly appended entry")
        assertTrue(thirdEntries.last().frames.isNotEmpty(), "Expected returned new entry to contain stack frames")
    }
}
