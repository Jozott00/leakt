package dev.jozott.leakt.test.utils

/**
 * LeakSanitizer entry kind as printed in reports.
 */
enum class LeakEntryKind {
    DIRECT,
    INDIRECT,
}

/**
 * One stack frame from a leak entry stack trace.
 */
data class LeakStackFrame(
    val index: Int,
    val address: String,
    val symbol: String,
)

/**
 * A single leak entry (for example "Direct leak of ...").
 */
data class LeakEntry(
    val kind: LeakEntryKind,
    val bytes: Long,
    val objects: Long,
    val frames: List<LeakStackFrame>,
)

/**
 * Parsed summary line for a leak report.
 */
data class LeakSummary(
    val tool: String,
    val bytesLeaked: Long,
    val allocations: Long,
)

/**
 * Parsed leak report containing detailed entries and optional summary.
 */
data class LeakReport(
    val tool: String?,
    val entries: List<LeakEntry>,
    val summary: LeakSummary?,
) {
    /**
     * Returns only entries present in this report but not in [previous], using
     * order-independent multiset subtraction of [LeakEntry].
     *
     * Returns `null` if there is no remaining entry after subtraction.
     */
    fun subtract(previous: LeakReport): LeakReport? {
        val remainingCounts = entries.groupingBy { it }.eachCount().toMutableMap()
        for (entry in previous.entries) {
            val count = remainingCounts[entry] ?: continue
            if (count <= 1) {
                remainingCounts.remove(entry)
            } else {
                remainingCounts[entry] = count - 1
            }
        }

        if (remainingCounts.isEmpty()) return null

        val newEntries = buildList {
            for (entry in entries) {
                val count = remainingCounts[entry] ?: continue
                add(entry)
                if (count <= 1) {
                    remainingCounts.remove(entry)
                } else {
                    remainingCounts[entry] = count - 1
                }
            }
        }

        if (newEntries.isEmpty()) return null

        val summaryTool = summary?.tool ?: tool ?: previous.summary?.tool ?: previous.tool ?: "LeakSanitizer"
        val newSummary = LeakSummary(
            tool = summaryTool,
            bytesLeaked = newEntries.sumOf { it.bytes },
            allocations = newEntries.sumOf { it.objects },
        )
        return LeakReport(tool = tool ?: previous.tool, entries = newEntries, summary = newSummary)
    }
}

/**
 * Line-based parser for LeakSanitizer stderr output.
 *
 * It supports multiple reports in one text block and tolerates unrelated lines.
 */
object LeakReportParser {
    private val errorLineRegex = Regex(""".*ERROR:\s+([A-Za-z]+Sanitizer):\s+detected memory leaks.*""")
    private val entryLineRegex =
        Regex("""(Direct|Indirect) leak of (\d+) byte\(s\) in (\d+) object\(s\) allocated from:""")
    private val frameLineRegex = Regex("""\s*#(\d+)\s+(0x[0-9a-fA-F]+)\s+(.*)""")
    private val summaryLineRegex =
        Regex(""".*SUMMARY:\s+([A-Za-z]+Sanitizer):\s+(\d+) byte\(s\) leaked in (\d+) allocation\(s\)\..*""")

    /**
     * Parses stderr text into zero or more [LeakReport] values.
     */
    fun parse(stderr: String): List<LeakReport> {
        val reports = mutableListOf<LeakReport>()

        var currentTool: String? = null
        var currentSummary: LeakSummary? = null
        val currentEntries = mutableListOf<LeakEntry>()
        var currentEntry: MutableLeakEntry? = null

        fun flushEntry() {
            val entry = currentEntry ?: return
            currentEntries += LeakEntry(
                kind = entry.kind,
                bytes = entry.bytes,
                objects = entry.objects,
                frames = entry.frames.toList(),
            )
            currentEntry = null
        }

        fun flushReport() {
            flushEntry()
            if (currentTool == null && currentSummary == null && currentEntries.isEmpty()) return
            reports += LeakReport(
                tool = currentTool ?: currentSummary?.tool,
                entries = currentEntries.toList(),
                summary = currentSummary,
            )
            currentTool = null
            currentSummary = null
            currentEntries.clear()
        }

        for (line in stderr.lineSequence()) {
            val errorMatch = errorLineRegex.matchEntire(line)
            if (errorMatch != null) {
                flushReport()
                currentTool = errorMatch.groupValues[1]
                continue
            }

            val entryMatch = entryLineRegex.matchEntire(line.trim())
            if (entryMatch != null) {
                flushEntry()
                currentEntry = MutableLeakEntry(
                    kind = if (entryMatch.groupValues[1] == "Direct") LeakEntryKind.DIRECT else LeakEntryKind.INDIRECT,
                    bytes = entryMatch.groupValues[2].toLong(),
                    objects = entryMatch.groupValues[3].toLong(),
                )
                continue
            }

            val frameMatch = frameLineRegex.matchEntire(line)
            if (frameMatch != null && currentEntry != null) {
                currentEntry?.frames?.add(
                    LeakStackFrame(
                        index = frameMatch.groupValues[1].toInt(),
                        address = frameMatch.groupValues[2],
                        symbol = frameMatch.groupValues[3].trim(),
                    )
                )
                continue
            }

            val summaryMatch = summaryLineRegex.matchEntire(line)
            if (summaryMatch != null) {
                flushEntry()
                currentSummary = LeakSummary(
                    tool = summaryMatch.groupValues[1],
                    bytesLeaked = summaryMatch.groupValues[2].toLong(),
                    allocations = summaryMatch.groupValues[3].toLong(),
                )
            }
        }

        flushReport()
        return reports
    }

    private data class MutableLeakEntry(
        val kind: LeakEntryKind,
        val bytes: Long,
        val objects: Long,
        val frames: MutableList<LeakStackFrame> = mutableListOf(),
    )
}
