package org.btmonier.agckt.benchmark

import java.nio.file.Files
import java.nio.file.Path

internal data class BenchmarkSampleRow(val scenario: String, val iteration: Int, val ms: Long)

internal fun defaultReportDir(): Path {
    val env = System.getenv("AGC_BENCHMARK_REPORT_DIR")
    val base = if (env != null) Path.of(env) else Path.of("build/reports/benchmark-data")
    Files.createDirectories(base)
    return base
}

/** Optional override: full path to CSV. Default: [defaultReportDir]/[defaultFileName]. */
internal fun resolveBenchmarkCsvPath(defaultFileName: String): Path {
    System.getenv("AGC_BENCHMARK_CSV")?.let { return Path.of(it) }
    return defaultReportDir().resolve(defaultFileName)
}

internal fun writeBenchmarkCsv(csvPath: Path, rows: List<BenchmarkSampleRow>) {
    Files.createDirectories(csvPath.parent)
    Files.writeString(
        csvPath,
        buildString {
            appendLine("scenario,iteration,ms")
            for (r in rows) {
                appendLine("${csvEscape(r.scenario)},${r.iteration},${r.ms}")
            }
        },
    )
}

private fun needsCsvEscape(s: String): Boolean =
    s.contains(',') || s.contains('"') || s.contains('\n') || s.contains('\r')

private fun csvEscape(s: String): String {
    if (!needsCsvEscape(s)) return s
    return '"' + s.replace("\"", "\"\"") + '"'
}

internal fun formatBenchmarkSummary(
    iterations: Int,
    queriesSize: Int,
    jnaSamples: List<Long>,
    cliPerQuerySamples: List<Long>,
    cliBatchSamples: List<Long>,
): String {
    val totalOps = iterations.toLong() * queriesSize
    val jnaMs = jnaSamples.sum()
    val cliPerQueryMs = cliPerQuerySamples.sum()
    val cliBatchMs = cliBatchSamples.sum()
    val jnaMsPerOp = "%.3f".format(jnaMs.toDouble() / totalOps)
    val cliPerQueryMsPerOp = "%.3f".format(cliPerQueryMs.toDouble() / totalOps)
    val cliBatchMsPerInv = "%.3f".format(cliBatchMs.toDouble() / iterations)
    return buildString {
        appendLine("Scenario (lower ms is better for same total work)")
        appendLine("=".repeat(60))
        appendLine(
            "JNA (1x open, ${iterations}x $queriesSize getSequence calls):  " +
                "$jnaMs ms total, $jnaMsPerOp ms/op",
        )
        appendLine(
            "CLI (1x process per region, ${iterations * queriesSize}x getctg): " +
                "$cliPerQueryMs ms total, $cliPerQueryMsPerOp ms/op",
        )
        appendLine(
            "CLI (1X getctg per batch, ${iterations}x $queriesSize regions each): " +
                "$cliBatchMs ms total, $cliBatchMsPerInv ms/invocation",
        )
        appendLine("=".repeat(60))
        val jnaSafe = maxOf(1, jnaMs).toDouble()
        appendLine(
            "Speedup JNA vs CLI-per-query: ${"%.2f".format(cliPerQueryMs / jnaSafe)}x " +
                "(if >1, JNA is faster)",
        )
        appendLine(
            "Speedup JNA vs CLI-per-batch:   ${"%.2f".format(cliBatchMs / jnaSafe)}x",
        )
    }
}

internal fun samplesToRows(
    jnaSamples: List<Long>,
    cliPerQuerySamples: List<Long>,
    cliBatchSamples: List<Long>,
): List<BenchmarkSampleRow> = buildList {
    jnaSamples.forEachIndexed { i, ms ->
        add(BenchmarkSampleRow(BenchmarkScenario.JNA.label, i + 1, ms))
    }
    cliPerQuerySamples.forEachIndexed { i, ms ->
        add(BenchmarkSampleRow(BenchmarkScenario.CLI_PER_QUERY.label, i + 1, ms))
    }
    cliBatchSamples.forEachIndexed { i, ms ->
        add(BenchmarkSampleRow(BenchmarkScenario.CLI_BATCH.label, i + 1, ms))
    }
}

/** JNA + CLI batch only (e.g. [RealWorldAgcBenchmarkKt] omits per-query CLI). */
internal fun samplesToRowsJnaAndBatch(jnaSamples: List<Long>, cliBatchSamples: List<Long>): List<BenchmarkSampleRow> =
    buildList {
        jnaSamples.forEachIndexed { i, ms ->
            add(BenchmarkSampleRow(BenchmarkScenario.JNA.label, i + 1, ms))
        }
        cliBatchSamples.forEachIndexed { i, ms ->
            add(BenchmarkSampleRow(BenchmarkScenario.CLI_BATCH.label, i + 1, ms))
        }
    }

internal fun formatBenchmarkSummaryJnaAndBatch(
    iterations: Int,
    queriesSize: Int,
    jnaSamples: List<Long>,
    cliBatchSamples: List<Long>,
): String {
    val totalOps = iterations.toLong() * queriesSize
    val jnaMs = jnaSamples.sum()
    val cliBatchMs = cliBatchSamples.sum()
    val jnaMsPerOp = "%.3f".format(jnaMs.toDouble() / totalOps)
    val cliBatchMsPerInv = "%.3f".format(cliBatchMs.toDouble() / iterations)
    val jnaSafe = maxOf(1, jnaMs).toDouble()
    return buildString {
        appendLine("Scenario (lower ms is better for same total work)")
        appendLine("=".repeat(60))
        appendLine(
            "JNA (1x open, ${iterations}x $queriesSize getSequence calls):  " +
                "$jnaMs ms total, $jnaMsPerOp ms/op",
        )
        appendLine(
            "CLI (1X getctg per batch, ${iterations}x $queriesSize regions each): " +
                "$cliBatchMs ms total, $cliBatchMsPerInv ms/invocation",
        )
        appendLine("=".repeat(60))
        appendLine(
            "Speedup JNA vs CLI batch: ${"%.2f".format(cliBatchMs / jnaSafe)}x " +
                "(if >1, JNA is faster)",
        )
    }
}

internal enum class BenchmarkScenario(val label: String) {
    JNA("JNA"),
    CLI_PER_QUERY("CLI per-query"),
    CLI_BATCH("CLI batch"),
}
