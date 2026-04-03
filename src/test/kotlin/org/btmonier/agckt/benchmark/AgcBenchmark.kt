package org.btmonier.agckt.benchmark

import org.btmonier.agckt.AgcArchive
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.measureTimeMillis

/**
 * Compares JNA ([AgcArchive]) against the [agc](https://github.com/refresh-bio/agc) CLI (`getctg`).
 *
 * Run: `./gradlew benchmark` (requires `native/lib` and `agc` on `PATH` - use `pixi run benchmark`).
 *
 * Environment:
 * - `AGC_BENCHMARK_ARCHIVE` - absolute or project-relative path to `.agc` (default: test fixture `toy_ex.agc` from classpath)
 * - `AGC_BENCHMARK_ITERATIONS` - outer repetitions (default: 200)
 * - `AGC_BENCHMARK_WARMUP` - warmup rounds (default: 20)
 *
 * Args: `[iterations] [warmup]` override env defaults.
 */
fun main(args: Array<String>) {
    val archivePath = resolveArchivePath()
    val iterations = args.getOrNull(0)?.toIntOrNull()
        ?: System.getenv("AGC_BENCHMARK_ITERATIONS")?.toIntOrNull()
        ?: 200
    val warmup = args.getOrNull(1)?.toIntOrNull()
        ?: System.getenv("AGC_BENCHMARK_WARMUP")?.toIntOrNull()
        ?: 20

    require(iterations > 0 && warmup >= 0)
    require(Files.exists(archivePath)) { "Archive not found: $archivePath" }

    checkAgcExists()

    // Toy fixture: see AgcArchiveTest - multiple samples and contigs
    val queries: List<RegionQuery> = listOf(
        RegionQuery("ref", "chr1", 0, 3),
        RegionQuery("ref", "chr1", 10, 15),
        RegionQuery("ref", "chr2", 0, 4),
        RegionQuery("a", "chr1a", 0, 5),
        RegionQuery("b", "chr1", 0, 2),
    )

    println("Archive: ${archivePath.toAbsolutePath()}")
    println("Queries: ${queries.size} regions x $iterations iterations (warmup $warmup)")
    println()

    repeat(warmup) {
        jnaOneOpen(archivePath, queries, 1)
        cliProcessPerQuery(archivePath, queries, 1)
        cliBatchPerIteration(archivePath, queries, 1)
    }

    val jnaMs = measureTimeMillis { jnaOneOpen(archivePath, queries, iterations) }
    val cliPerQueryMs = measureTimeMillis { cliProcessPerQuery(archivePath, queries, iterations) }
    val cliBatchMs = measureTimeMillis { cliBatchPerIteration(archivePath, queries, iterations) }

    val totalOps = iterations.toLong() * queries.size
    println(
        buildString {
            appendLine("Scenario (lower ms is better for same total work)")
            appendLine("=".repeat(60))
            appendLine(
                "JNA (1x open, ${iterations}x ${queries.size} getSequence calls):  " +
                    "$jnaMs ms total, ${"%.3f".format(jnaMs.toDouble() / totalOps)} ms/op",
            )
            appendLine(
                "CLI (1x process per region, ${iterations * queries.size}x getctg): " +
                    "$cliPerQueryMs ms total, ${"%.3f".format(cliPerQueryMs.toDouble() / totalOps)} ms/op",
            )
            appendLine(
                "CLI (1X getctg per batch, ${iterations}x ${queries.size} regions each): " +
                    "$cliBatchMs ms total, ${"%.3f".format(cliBatchMs.toDouble() / iterations)} ms/invocation",
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
        },
    )
}

internal data class RegionQuery(val sample: String, val contig: String, val start: Int, val end: Int)

/** AGC `getctg` uses 0-based inclusive ranges (same as [AgcArchive.getSequence]). */
internal fun RegionQuery.toCliSpec(): String = "$contig@$sample:$start-$end"

private object FixtureResource

private fun resolveArchivePath(): Path {
    System.getenv("AGC_BENCHMARK_ARCHIVE")?.let { return Path.of(it) }
    val url = FixtureResource::class.java.classLoader.getResource("fixtures/toy_ex.agc")
        ?: error("Missing fixture fixtures/toy_ex.agc - set AGC_BENCHMARK_ARCHIVE")
    return Path.of(url.toURI())
}

internal fun checkAgcExists() {
    try {
        val p = ProcessBuilder("agc").start()
        sinkProcess(p)
    } catch (e: IOException) {
        throw IllegalStateException(
            "agc CLI not found on PATH (install via pixi, or run: pixi run benchmark)",
            e,
        )
    }
}

private fun sinkProcess(p: Process) {
    p.inputStream.use { it.copyTo(OutputStream.nullOutputStream()) }
    p.errorStream?.use { it.copyTo(OutputStream.nullOutputStream()) }
    val code = p.waitFor()
    if (code != 0) {
        error("agc exited with code $code")
    }
}

/** One archive open; repeat [iterations] times all queries (typical JNA usage). */
internal fun jnaOneOpen(archivePath: Path, queries: List<RegionQuery>, iterations: Int) {
    AgcArchive.open(archivePath).use { archive ->
        repeat(iterations) {
            for (q in queries) {
                archive.getSequence(q.contig, q.start, q.end, q.sample)
            }
        }
    }
}

/** One `agc getctg` process per query (worst case for subprocess overhead). */
internal fun cliProcessPerQuery(archivePath: Path, queries: List<RegionQuery>, iterations: Int) {
    val abs = archivePath.toAbsolutePath().toString()
    repeat(iterations) {
        for (q in queries) {
            val pb = ProcessBuilder("agc", "getctg", abs, q.toCliSpec())
            val p = pb.start()
            sinkProcess(p)
        }
    }
}

/** One `agc getctg` per outer iteration with all regions (fairer CLI baseline). */
internal fun cliBatchPerIteration(archivePath: Path, queries: List<RegionQuery>, iterations: Int) {
    val abs = archivePath.toAbsolutePath().toString()
    repeat(iterations) {
        val cmd = ArrayList<String>(3 + queries.size).apply {
            add("agc")
            add("getctg")
            add(abs)
            queries.forEach { q -> add(q.toCliSpec()) }
        }
        val pb = ProcessBuilder(cmd)
        val p = pb.start()
        sinkProcess(p)
    }
}
