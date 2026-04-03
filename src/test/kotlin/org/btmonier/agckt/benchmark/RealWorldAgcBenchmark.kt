package org.btmonier.agckt.benchmark

import io.github.cdimascio.dotenv.Dotenv
import org.btmonier.agckt.AgcArchive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.measureTimeMillis

/**
 * Same JNA vs `agc getctg` comparison as [AgcBenchmarkKt], but against a real archive whose path
 * comes from **`AGC_ARCHIVE_PATH`** in a **`.env`** file at the project root (or the environment),
 * matching [DemoKt].
 *
 * Builds one query per chromosome **`chr1` … `chr10`** on the reference sample (or the first listed
 * sample if no reference is set): the first up to 100 bp of each contig (clamped to contig length).
 *
 * Run: `./gradlew benchmarkReal` (requires `agc` on `PATH`, `native/lib`, and a valid `.env` — use
 * `pixi run benchmark-real`).
 *
 * Environment (same as [AgcBenchmarkKt] for timing knobs):
 * - `AGC_BENCHMARK_ITERATIONS` / `AGC_BENCHMARK_WARMUP` — or args `[iterations] [warmup]`
 */
fun main(args: Array<String>) {
    val archivePath = resolveArchivePathFromEnv()
    require(Files.exists(archivePath)) { "Archive not found: $archivePath" }

    val iterations = args.getOrNull(0)?.toIntOrNull()
        ?: System.getenv("AGC_BENCHMARK_ITERATIONS")?.toIntOrNull()
        ?: 25
    val warmup = args.getOrNull(1)?.toIntOrNull()
        ?: System.getenv("AGC_BENCHMARK_WARMUP")?.toIntOrNull()
        ?: 5

    require(iterations > 0 && warmup >= 0)

    checkAgcExists()

    val queries = AgcArchive.open(archivePath).use { archive ->
        val sample = archive.referenceSample
            ?: archive.listSamples().firstOrNull()
            ?: error("Archive has no samples")
        buildQueriesForChr1To10(archive, sample)
    }

    println("Archive: ${archivePath.toAbsolutePath()}")
    println("Sample: ${queries.first().sample} (regions on chr1–chr10)")
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

private const val FIRST_WINDOW_LAST_INDEX = 99 // 100 bp: indices 0..99 inclusive

/**
 * AGC may return decorated IDs (e.g. `chr1 sampleName=B73`). The leading token before the first
 * space is the contig key we match against `chr1`…`chr10`; queries use the full string from the archive.
 */
private fun contigKey(agcListedName: String): String = agcListedName.substringBefore(" ").trim()

private fun buildQueriesForChr1To10(archive: AgcArchive, sample: String): List<RegionQuery> {
    val byKey = archive.listContigs(sample).associateBy { contigKey(it) }
    val queries = ArrayList<RegionQuery>(10)
    for (n in 1..10) {
        val key = "chr$n"
        val contigId = byKey[key]
            ?: error(
                "Expected contig '$key' for sample '$sample'. Available: " +
                    byKey.keys.filter { it.startsWith("chr") }.sorted().joinToString(),
            )
        val len = archive.contigLength(contigId, sample)
        require(len > 0) { "Contig '$contigId' has length 0 for sample '$sample'" }
        val end = minOf(FIRST_WINDOW_LAST_INDEX, len - 1)
        queries.add(RegionQuery(sample, contigId, 0, end))
    }
    return queries
}

private fun resolveArchivePathFromEnv(): Path {
    val dotenv = Dotenv.configure()
        .directory(System.getProperty("user.dir"))
        .ignoreIfMissing()
        .load()
    val path = dotenv["AGC_ARCHIVE_PATH"]
        ?: System.getenv("AGC_ARCHIVE_PATH")
        ?: error(
            "Missing AGC archive path. Set AGC_ARCHIVE_PATH in a .env file at the project root " +
                "(see .env.example) or in the environment.",
        )
    return Path.of(path)
}
