@file:OptIn(ExperimentalForeignApi::class)

package dev.jozott.leakt.test.utils

import dev.jozott.leakt.LeakDetectedException
import dev.jozott.leakt.LeakReporting
import dev.jozott.leakt.LeakSanitizer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.refTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import platform.posix.STDERR_FILENO
import platform.posix.STDOUT_FILENO
import platform.posix._IO_FILE
import platform.posix.close
import platform.posix.dup
import platform.posix.dup2
import platform.posix.fflush
import platform.posix.pipe
import platform.posix.read
import platform.posix.stderr
import platform.posix.stdout
import kotlin.test.assertIs

/**
 * Output stream descriptor used by [captureOutput].
 */
sealed interface CaptureOut {
    val fd: Int
    val file:  CPointer<_IO_FILE>?

    object StdErr : CaptureOut {
        override val fd: Int = STDERR_FILENO
        override val file: CPointer<_IO_FILE>? = stderr
    }
    object StdOut : CaptureOut {
        override val fd: Int = STDOUT_FILENO
        override val file: CPointer<_IO_FILE>? = stdout
    }
}

// Process-global baseline of the most recently parsed leak report.
// This intentionally mirrors LeakSanitizer process-global behavior.
private var lastCapturedLeakReport: LeakReport? = null

/**
 * Runs [body] while capturing stderr, parses at most one leak report, and returns only
 * the leak entries that are new compared to the previous call in this process.
 *
 * Returns:
 * - `null` when no report exists or no new leak entries were added.
 * - A [LeakReport] containing only newly observed entries otherwise.
 */
fun captureNewLeaks(body: () -> Unit): LeakReport? {
    val output = captureOutput(CaptureOut.StdErr, body)
    val reports = LeakReportParser.parse(output.first)
    check(reports.size <= 1) { "Expected at most one leak report, got ${reports.size}" }
    val exception = output.second
    if (exception != null) assertIs<LeakDetectedException>(exception)

    val currentReport = reports.firstOrNull() ?: return null
    val previousReport = lastCapturedLeakReport
    lastCapturedLeakReport = currentReport
    return if (previousReport == null) currentReport else currentReport.subtract(previousReport)
}

/**
 * Triggers a leak check and returns the raw textual output printed to stdout.
 */
fun currentLeaks(): String {
    return captureOutput(CaptureOut.StdOut) {
        LeakSanitizer.checkLeaks(reporting = LeakReporting.REPEAT)
    }.first
}

/**
 * Captures output of [block] from [out] and returns `(capturedText, thrownException)`.
 */
fun captureOutput(out: CaptureOut, block: () -> Unit): Pair<String, Throwable?> = runBlocking { memScoped {
    val pipeErr = allocArray<IntVar>(2)
    check(pipe(pipeErr) == 0)

    val savedStderr = dup(out.fd)

    check(dup2(pipeErr[1], out.fd) != -1)
    close(pipeErr[1])

    val output = StringBuilder()

    val reader = launch(Dispatchers.IO) {
        val buf = ByteArray(4096)
        var r: Long
        do {
            r = read(pipeErr[0], buf.refTo(0), buf.size.convert()).convert()
            if (r > 0) output.append(buf.decodeToString(0, r.convert()))
        } while (r > 0)
        close(pipeErr[0])
    }

    var exception: Throwable? = null;
    try {
        block()
    } catch (e: Throwable) {
        exception = e
    } finally {
        fflush(out.file)
        dup2(savedStderr, out.fd)
        close(savedStderr)
    }

    reader.join()
    output.toString() to exception
} }
