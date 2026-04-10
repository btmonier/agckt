package org.btmonier.agckt.benchmark

import io.github.cdimascio.dotenv.Dotenv
import org.btmonier.agckt.AgcArchive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.measureTimeMillis

/**
 * JNA vs batched `agc getctg` on **`AGC_ARCHIVE_PATH`** (from **`.env`** or the environment), tiling a
 * chosen contig (**`AGC_BENCHMARK_CONTIG`**, default **`chr1`**) into consecutive non-overlapping windows
 * whose lengths cycle through **5000, 7500, and 10000 bp** (all in the 5–10k range). The last window may
 * be shorter if the contig does not divide evenly.
 *
 * The CLI path uses [cliBatchPerIteration] when **`AGC_BENCHMARK_CLI_CHUNK_SIZE` is `0`** (one `agc getctg`
 * with every region), or [cliBatchChunkedPerIteration] when the chunk size is **> 0** (avoids huge argv).
 *
 * Run: `./gradlew benchmarkFullChrRegions` (requires `agc` on `PATH`, `native/lib`, valid `.env`).
 *
 * Environment (extends [RealWorldAgcBenchmarkKt]):
 * - `AGC_BENCHMARK_ITERATIONS` / `AGC_BENCHMARK_WARMUP` - or args `[iterations] [warmup]`
 * - `AGC_BENCHMARK_CLI_CHUNK_SIZE` in **`.env`** or the environment - **`0`** = single `getctg` with all regions
 *   (default **0**); **`> 0`** = at most that many regions per subprocess if the OS rejects a long argv
 * - `AGC_BENCHMARK_CONTIG` in **`.env`** or the environment - contig key to tile (default **`chr1`**; the
 *   leading token before the first space in each listed contig name)
 * - `AGC_BENCHMARK_REPORT_DIR` / `AGC_BENCHMARK_CSV` - CSV default `full_chr_regions_benchmark.csv`
 */
fun main(args: Array<String>) {
    val dotenv = Dotenv.configure()
        .directory(System.getProperty("user.dir"))
        .ignoreIfMissing()
        .load()
    runFullChrRegionsBenchmark(args, dotenv)
}

private data class FullChrBenchConfig(
    val iterations: Int,
    val warmup: Int,
    val maxRegionsPerProcess: Int,
    val contigKey: String,
)

private fun parseFullChrBenchConfig(args: Array<String>, dotenv: Dotenv): FullChrBenchConfig {
    val iterations = args.getOrNull(0)?.toIntOrNull()
        ?: System.getenv("AGC_BENCHMARK_ITERATIONS")?.toIntOrNull()
        ?: 5
    val warmup = args.getOrNull(1)?.toIntOrNull()
        ?: System.getenv("AGC_BENCHMARK_WARMUP")?.toIntOrNull()
        ?: 2
    val maxRegionsPerProcess = dotenv["AGC_BENCHMARK_CLI_CHUNK_SIZE"]?.toIntOrNull()
        ?: System.getenv("AGC_BENCHMARK_CLI_CHUNK_SIZE")?.toIntOrNull()
        ?: 0
    require(maxRegionsPerProcess >= 0) {
        "AGC_BENCHMARK_CLI_CHUNK_SIZE must be >= 0 (0 = one getctg with all regions)"
    }
    require(iterations > 0 && warmup >= 0)
    val contigKey = resolveContigKeyFromEnv(dotenv)
    return FullChrBenchConfig(iterations, warmup, maxRegionsPerProcess, contigKey)
}

/** Reads `AGC_BENCHMARK_CONTIG` from [dotenv] or the environment; defaults to `chr1`. */
private fun resolveContigKeyFromEnv(dotenv: Dotenv): String {
    val raw = dotenv["AGC_BENCHMARK_CONTIG"]?.trim()?.takeIf { it.isNotEmpty() }
        ?: System.getenv("AGC_BENCHMARK_CONTIG")?.trim()?.takeIf { it.isNotEmpty() }
        ?: "chr1"
    return raw
}

private fun runFullChrRegionsBenchmark(args: Array<String>, dotenv: Dotenv) {
    val config = parseFullChrBenchConfig(args, dotenv)
    val archivePath = resolveArchivePathFromEnv(dotenv)
    require(Files.exists(archivePath)) { "Archive not found: $archivePath" }

    checkAgcExists()

    val queries = AgcArchive.open(archivePath, prefetch = false).use { archive ->
        val sample = archive.referenceSample
            ?: archive.listSamples().firstOrNull()
            ?: error("Archive has no samples")
        buildContigTiledQueries5to10k(archive, sample, config.contigKey)
    }

    val cliBatchesPerIteration =
        if (config.maxRegionsPerProcess == 0) {
            1
        } else {
            (queries.size + config.maxRegionsPerProcess - 1) / config.maxRegionsPerProcess
        }

    printFullChrBenchmarkHeader(archivePath, queries, config, cliBatchesPerIteration)

    val (jnaSamples, cliBatchSamples) = warmupAndMeasureFullChr(archivePath, queries, config)

    val csvPath = resolveBenchmarkCsvPath("full_chr_regions_benchmark.csv")
    writeBenchmarkCsv(csvPath, samplesToRowsJnaAndBatch(jnaSamples, cliBatchSamples))
    println("Raw samples written: ${csvPath.toAbsolutePath()}")
    println()
    println(
        formatBenchmarkSummaryFullChrRegions(
            config.iterations,
            queries.size,
            jnaSamples,
            cliBatchSamples,
            cliBatchesPerIteration,
            config.maxRegionsPerProcess,
        ),
    )
}

private fun printFullChrBenchmarkHeader(
    archivePath: Path,
    queries: List<RegionQuery>,
    config: FullChrBenchConfig,
    cliBatchesPerIteration: Int,
) {
    println("Archive: ${archivePath.toAbsolutePath()}")
    println(
        "Sample: ${queries.first().sample} (${config.contigKey} tiled 5–10 kbp windows, ${queries.size} regions)",
    )
    println(
        if (config.maxRegionsPerProcess == 0) {
            "CLI: single getctg per outer iteration with all ${queries.size} regions"
        } else {
            "CLI: up to ${config.maxRegionsPerProcess} regions per getctg → $cliBatchesPerIteration subprocess(es) " +
                "per outer iteration"
        },
    )
    println("Queries: ${queries.size} regions x ${config.iterations} iterations (warmup ${config.warmup})")
    println()
}

private fun warmupAndMeasureFullChr(
    archivePath: Path,
    queries: List<RegionQuery>,
    config: FullChrBenchConfig,
): Pair<List<Long>, List<Long>> {
    repeat(config.warmup) {
        jnaOneOpen(archivePath, queries, 1, prefetch = false)
        runCliBatch(archivePath, queries, 1, config.maxRegionsPerProcess)
    }
    val jnaSamples = List(config.iterations) {
        measureTimeMillis { jnaOneOpen(archivePath, queries, 1, prefetch = false) }
    }
    val cliBatchSamples = List(config.iterations) {
        measureTimeMillis {
            runCliBatch(archivePath, queries, 1, config.maxRegionsPerProcess)
        }
    }
    return jnaSamples to cliBatchSamples
}

/** `maxRegionsPerProcess == 0` → [cliBatchPerIteration]; else [cliBatchChunkedPerIteration]. */
private fun runCliBatch(archivePath: Path, queries: List<RegionQuery>, iterations: Int, maxRegionsPerProcess: Int) {
    if (maxRegionsPerProcess == 0) {
        cliBatchPerIteration(archivePath, queries, iterations)
    } else {
        cliBatchChunkedPerIteration(archivePath, queries, iterations, maxRegionsPerProcess)
    }
}

/** Cycle 5 kb, 7.5 kb, 10 kb - all within 5–10 kbp. */
private val WINDOW_LENGTHS_BP = intArrayOf(5000, 7500, 10_000)

private fun contigKey(agcListedName: String): String = agcListedName.substringBefore(" ").trim()

/**
 * Tiles the contig whose leading key matches [requestedContigKey] (e.g. `chr1`) from base 0 with repeating
 * window lengths [WINDOW_LENGTHS_BP]. The final segment may be shorter than 5000 bp if the contig end is reached.
 */
private fun buildContigTiledQueries5to10k(
    archive: AgcArchive,
    sample: String,
    requestedContigKey: String,
): List<RegionQuery> {
    val byKey = archive.listContigs(sample).associateBy { contigKey(it) }
    val contigId = byKey[requestedContigKey]
        ?: error(
            "Expected contig '$requestedContigKey' for sample '$sample'. Available: " +
                byKey.keys.sorted().joinToString(),
        )
    val len = archive.contigLength(contigId, sample)
    require(len > 0) { "Contig '$contigId' has length 0 for sample '$sample'" }

    val queries = ArrayList<RegionQuery>(len / 5000 + 16)
    var pos = 0
    var i = 0
    while (pos < len) {
        val remaining = len - pos
        val desired = WINDOW_LENGTHS_BP[i % WINDOW_LENGTHS_BP.size]
        val windowLen = minOf(desired, remaining)
        val end = pos + windowLen - 1
        queries.add(RegionQuery(sample, contigId, pos, end))
        pos = end + 1
        i++
    }
    return queries
}

private fun resolveArchivePathFromEnv(dotenv: Dotenv): Path {
    val path = dotenv["AGC_ARCHIVE_PATH"]
        ?: System.getenv("AGC_ARCHIVE_PATH")
        ?: error(
            "Missing AGC archive path. Set AGC_ARCHIVE_PATH in a .env file at the project root " +
                "(see .env.example) or in the environment.",
        )
    return Path.of(path)
}

private fun formatBenchmarkSummaryFullChrRegions(
    iterations: Int,
    queriesSize: Int,
    jnaSamples: List<Long>,
    cliBatchSamples: List<Long>,
    cliBatchesPerIteration: Int,
    maxRegionsPerProcess: Int,
): String {
    val totalOps = iterations.toLong() * queriesSize
    val jnaMs = jnaSamples.sum()
    val cliBatchMs = cliBatchSamples.sum()
    val jnaMsPerOp = "%.3f".format(jnaMs.toDouble() / totalOps)
    val cliBatchMsPerInv = "%.3f".format(cliBatchMs.toDouble() / iterations)
    val totalCliSubprocesses = iterations.toLong() * cliBatchesPerIteration
    val cliBatchMsPerSub = "%.3f".format(cliBatchMs.toDouble() / totalCliSubprocesses)
    val jnaSafe = maxOf(1, jnaMs).toDouble()
    val cliModeLine =
        if (maxRegionsPerProcess == 0) {
            "CLI (1 getctg per iter, all $queriesSize regions): " +
                "$cliBatchMs ms total, $cliBatchMsPerInv ms/outer-iter, $cliBatchMsPerSub ms/subprocess"
        } else {
            "CLI (chunked getctg, ≤$maxRegionsPerProcess regions/process, " +
                "${iterations}x $cliBatchesPerIteration subprocess(es)/iter): " +
                "$cliBatchMs ms total, $cliBatchMsPerInv ms/outer-iter, $cliBatchMsPerSub ms/subprocess"
        }
    return buildString {
        appendLine("Scenario (lower ms is better for same total work)")
        appendLine("=".repeat(60))
        appendLine(
            "JNA (1x open, ${iterations}x $queriesSize getSequence calls):  " +
                "$jnaMs ms total, $jnaMsPerOp ms/op",
        )
        appendLine(cliModeLine)
        appendLine("=".repeat(60))
        appendLine(
            "Speedup JNA vs CLI batch: ${"%.2f".format(cliBatchMs / jnaSafe)}x " +
                "(if >1, JNA is faster)",
        )
    }
}
