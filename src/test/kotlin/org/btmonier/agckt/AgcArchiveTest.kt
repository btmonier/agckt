package org.btmonier.agckt

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Path

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgcArchiveTest {

    private lateinit var fixturePath: Path

    @BeforeAll
    fun setUp() {
        val url = javaClass.classLoader.getResource("fixtures/toy_ex.agc")
        assumeTrue(url != null, "toy_ex.agc fixture not found")
        fixturePath = Path.of(url!!.toURI())
    }

    @Nested
    inner class Lifecycle {

        @Test
        fun `open and close succeeds`() {
            val archive = AgcArchive.open(fixturePath)
            archive.close()
        }

        @Test
        fun `use block auto-closes`() {
            AgcArchive.open(fixturePath).use { archive ->
                archive.sampleCount shouldBeGreaterThan 0
            }
        }

        @Test
        fun `double close is safe`() {
            val archive = AgcArchive.open(fixturePath)
            archive.close()
            archive.close()
        }

        @Test
        fun `operations after close throw IllegalStateException`() {
            val archive = AgcArchive.open(fixturePath)
            archive.close()
            shouldThrow<IllegalStateException> {
                archive.sampleCount
            }
        }

        @Test
        fun `open nonexistent file throws AgcException`() {
            shouldThrow<AgcException> {
                AgcArchive.open(Path.of("/nonexistent/path/archive.agc"))
            }
        }

        @Test
        fun `open with prefetch false works`() {
            AgcArchive.open(fixturePath, prefetch = false).use { archive ->
                archive.sampleCount shouldBe 4
            }
        }
    }

    @Nested
    inner class SampleQueries {

        @Test
        fun `sampleCount returns 4`() {
            AgcArchive.open(fixturePath).use { archive ->
                archive.sampleCount shouldBe 4
            }
        }

        @Test
        fun `listSamples returns all sample names`() {
            AgcArchive.open(fixturePath).use { archive ->
                archive.listSamples() shouldContainExactlyInAnyOrder listOf("ref", "a", "b", "c")
            }
        }

        @Test
        fun `referenceSample returns non-null`() {
            AgcArchive.open(fixturePath).use { archive ->
                archive.referenceSample.shouldNotBeEmpty()
            }
        }
    }

    @Nested
    inner class ContigQueries {

        @Test
        fun `contigCount for ref sample is 4`() {
            AgcArchive.open(fixturePath).use { archive ->
                archive.contigCount("ref") shouldBe 4
            }
        }

        @Test
        fun `contigCount for a sample is 2`() {
            AgcArchive.open(fixturePath).use { archive ->
                archive.contigCount("a") shouldBe 2
            }
        }

        @Test
        fun `listContigs for ref sample`() {
            AgcArchive.open(fixturePath).use { archive ->
                archive.listContigs("ref") shouldContainExactlyInAnyOrder
                    listOf("chr1", "chr2", "chr3", "seq")
            }
        }

        @Test
        fun `listContigs for sample c`() {
            AgcArchive.open(fixturePath).use { archive ->
                archive.listContigs("c") shouldContainExactlyInAnyOrder
                    listOf("1", "2", "3")
            }
        }

        @Test
        fun `listContigs with null sample returns all contigs`() {
            AgcArchive.open(fixturePath).use { archive ->
                val allContigs = archive.listContigs(null)
                allContigs.shouldHaveSize(13)
            }
        }

        @Test
        fun `contigLength for known contigs`() {
            AgcArchive.open(fixturePath).use { archive ->
                archive.contigLength("chr1", "ref") shouldBe 16
                archive.contigLength("chr2", "ref") shouldBe 15
                archive.contigLength("chr3", "ref") shouldBe 14
                archive.contigLength("seq", "ref") shouldBe 10
                archive.contigLength("chr1a", "a") shouldBe 13
                archive.contigLength("3", "c") shouldBe 16
            }
        }

        @Test
        fun `contigLength with null sample for unique contig`() {
            AgcArchive.open(fixturePath).use { archive ->
                archive.contigLength("chr1a") shouldBe 13
            }
        }

        @Test
        fun `contigLength for nonexistent contig throws`() {
            AgcArchive.open(fixturePath).use { archive ->
                shouldThrow<AgcNativeException> {
                    archive.contigLength("nonexistent", "ref")
                }
            }
        }
    }

    @Nested
    inner class SequenceExtraction {

        @Test
        fun `getSequence full contig from ref`() {
            AgcArchive.open(fixturePath).use { archive ->
                archive.getSequence("chr1", sample = "ref") shouldBe "AGCTAGCTAGCTAGCT"
                archive.getSequence("chr2", sample = "ref") shouldBe "TAAAAAAAAAAATTT"
                archive.getSequence("chr3", sample = "ref") shouldBe "TGGGGGGGGGGTTT"
                archive.getSequence("seq", sample = "ref") shouldBe "TGTGTGTGTG"
            }
        }

        @Test
        fun `getSequence full contig from sample a`() {
            AgcArchive.open(fixturePath).use { archive ->
                archive.getSequence("chr1a", sample = "a") shouldBe "CTGAGCTGACTGA"
                archive.getSequence("chr3a", sample = "a") shouldBe "AGTTTAGCT"
            }
        }

        @Test
        fun `getSequence full contig from sample b`() {
            AgcArchive.open(fixturePath).use { archive ->
                archive.getSequence("chr1", sample = "b") shouldBe "AAAAAAAAA"
                archive.getSequence("c", sample = "b") shouldBe "CCCCCCCCC"
                archive.getSequence("t", sample = "b") shouldBe "TTTTTTT"
            }
        }

        @Test
        fun `getSequence full contig from sample c`() {
            AgcArchive.open(fixturePath).use { archive ->
                archive.getSequence("1", sample = "c") shouldBe "TGTGTGTGTGTG"
                archive.getSequence("2", sample = "c") shouldBe "ACACACACA"
                archive.getSequence("3", sample = "c") shouldBe "TTTTCCCGGGAAAAAA"
            }
        }

        @Test
        fun `getSequence with null sample for unique contig`() {
            AgcArchive.open(fixturePath).use { archive ->
                archive.getSequence("chr1a") shouldBe "CTGAGCTGACTGA"
            }
        }

        @Test
        fun `getSequence slice - first 4 bases`() {
            AgcArchive.open(fixturePath).use { archive ->
                archive.getSequence("chr1", start = 0, end = 3, sample = "ref") shouldBe "AGCT"
            }
        }

        @Test
        fun `getSequence slice - last 4 bases`() {
            AgcArchive.open(fixturePath).use { archive ->
                archive.getSequence("chr1", start = 12, end = 15, sample = "ref") shouldBe "AGCT"
            }
        }

        @Test
        fun `getSequence slice - middle`() {
            AgcArchive.open(fixturePath).use { archive ->
                archive.getSequence("chr1", start = 4, end = 7, sample = "ref") shouldBe "AGCT"
            }
        }

        @Test
        fun `getSequence slice - single base`() {
            AgcArchive.open(fixturePath).use { archive ->
                archive.getSequence("chr1", start = 0, end = 0, sample = "ref") shouldBe "A"
            }
        }

        @Test
        fun `getSequence slice - entire contig`() {
            AgcArchive.open(fixturePath).use { archive ->
                archive.getSequence("seq", start = 0, end = 9, sample = "ref") shouldBe "TGTGTGTGTG"
            }
        }

        @Test
        fun `getSequence with negative start throws`() {
            AgcArchive.open(fixturePath).use { archive ->
                shouldThrow<IllegalArgumentException> {
                    archive.getSequence("chr1", start = -1, end = 3, sample = "ref")
                }
            }
        }

        @Test
        fun `getSequence with end less than start throws`() {
            AgcArchive.open(fixturePath).use { archive ->
                shouldThrow<IllegalArgumentException> {
                    archive.getSequence("chr1", start = 5, end = 3, sample = "ref")
                }
            }
        }
    }
}
