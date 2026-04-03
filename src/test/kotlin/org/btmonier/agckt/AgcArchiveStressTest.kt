package org.btmonier.agckt

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Path

/**
 * Stress tests to detect handle leaks and memory issues.
 */
@Tag("stress")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgcArchiveStressTest {

    private lateinit var fixturePath: Path

    @BeforeAll
    fun setUp() {
        val url = javaClass.classLoader.getResource("fixtures/toy_ex.agc")
        assumeTrue(url != null, "toy_ex.agc fixture not found")
        fixturePath = Path.of(url!!.toURI())
    }

    @Test
    fun `repeated open and close does not leak handles`() {
        repeat(500) {
            AgcArchive.open(fixturePath).use { archive ->
                archive.sampleCount shouldBe 4
            }
        }
    }

    @Test
    fun `repeated listSamples does not leak native memory`() {
        AgcArchive.open(fixturePath).use { archive ->
            repeat(1000) {
                val samples = archive.listSamples()
                samples.size shouldBe 4
            }
        }
    }

    @Test
    fun `repeated listContigs does not leak native memory`() {
        AgcArchive.open(fixturePath).use { archive ->
            repeat(1000) {
                val contigs = archive.listContigs("ref")
                contigs.size shouldBe 4
            }
        }
    }

    @Test
    fun `repeated getSequence does not leak memory`() {
        AgcArchive.open(fixturePath).use { archive ->
            repeat(1000) {
                val seq = archive.getSequence("chr1", sample = "ref")
                seq shouldBe "AGCTAGCTAGCTAGCT"
            }
        }
    }

    @Test
    fun `repeated referenceSample does not leak native strings`() {
        AgcArchive.open(fixturePath).use { archive ->
            repeat(1000) {
                archive.referenceSample
            }
        }
    }
}
