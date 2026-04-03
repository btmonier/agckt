/**
 * Basic usage example for agckt.
 *
 * Run with:
 *   kotlinc -cp agckt-0.1.0-SNAPSHOT.jar:jna-5.16.0.jar -script BasicUsage.kt genomes.agc
 *
 * Or from a Gradle project that depends on agckt.
 */

import org.btmonier.agckt.AgcArchive
import java.nio.file.Path

fun main(args: Array<String>) {
    val archivePath = args.firstOrNull()
        ?: error("Usage: BasicUsage <path-to-agc-archive>")

    AgcArchive.open(Path.of(archivePath)).use { archive ->
        println("AGC Archive: $archivePath")
        println("Samples: ${archive.sampleCount}")
        println("Reference sample: ${archive.referenceSample ?: "(none)"}")
        println()

        for (sample in archive.listSamples()) {
            val contigs = archive.listContigs(sample)
            println("Sample: $sample (${contigs.size} contigs)")

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
