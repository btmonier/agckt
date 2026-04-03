package org.btmonier.agckt.benchmark

import io.github.cdimascio.dotenv.Dotenv
import org.btmonier.agckt.AgcArchive
import java.nio.file.Path

/**
 * Runnable example: open a real-world AGC archive and pull multiple contig sequences for a sample.
 *
 * Set **`AGC_ARCHIVE_PATH`** in a **`.env`** file at the project root (see `.env.example`), or export it in the environment.
 *
 * Run `main` from your IDE (ensure `jna.library.path` includes this project’s `native/lib`, same as the
 * `test` task in `build.gradle.kts`).
 */
fun main() {
    val dotenv = Dotenv.configure()
        .directory(System.getProperty("user.dir"))
        .ignoreIfMissing()
        .load()

    val archivePath = dotenv["AGC_ARCHIVE_PATH"]
        ?: System.getenv("AGC_ARCHIVE_PATH")
        ?: error(
            "Missing AGC archive path. Set AGC_ARCHIVE_PATH in a .env file at the project root " +
                "(see .env.example) or in the environment.",
        )

    AgcArchive.open(Path.of(archivePath)).use { archive ->
        println("AGC Archive: $archivePath")
        println("Samples: ${archive.sampleCount}")
        println("Reference sample: ${archive.referenceSample ?: "(none)"}")
        println()

        for (sample in archive.listSamples()) {
            val contigs = archive.listContigs(sample).filter { it.startsWith("chr") }
            println("Sample: $sample (${contigs.size} chr*-prefixed contigs)")

            for (contig in contigs) {
                val len = archive.contigLength(contig, sample)
                println("  $contig: $len bp")

                if (len <= 100) {
                    val seq = archive.getSequence(contig, sample = sample)
                    println("    $seq")
                } else {
                    val preview = archive.getSequence(contig, start = 0, end = 49, sample = sample)
                    println("    ${preview}... (truncated)")
                }
            }
            println()
        }
    }
}
