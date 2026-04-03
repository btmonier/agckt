package org.btmonier.agckt

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Path

/**
 * Edge case tests for unusual inputs and boundary conditions.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgcArchiveEdgeCaseTest {

    private lateinit var fixturePath: Path

    @BeforeAll
    fun setUp() {
        val url = javaClass.classLoader.getResource("fixtures/toy_ex.agc")
        assumeTrue(url != null, "toy_ex.agc fixture not found")
        fixturePath = Path.of(url!!.toURI())
    }

    @Test
    fun `contig name with spaces - b sample has 'g h i 21'`() {
        AgcArchive.open(fixturePath).use { archive ->
            val contigs = archive.listContigs("b")
            contigs shouldContain "g h i 21"
            archive.contigLength("g h i 21", sample = "b") shouldBe 7
            archive.getSequence("g h i 21", sample = "b") shouldBe "GGGAGGG"
        }
    }

    @Test
    fun `single-character contig names in sample c`() {
        AgcArchive.open(fixturePath).use { archive ->
            archive.getSequence("1", sample = "c") shouldBe "TGTGTGTGTGTG"
            archive.getSequence("2", sample = "c") shouldBe "ACACACACA"
        }
    }

    @Test
    fun `single-character contig names in sample b`() {
        AgcArchive.open(fixturePath).use { archive ->
            archive.getSequence("c", sample = "b") shouldBe "CCCCCCCCC"
            archive.getSequence("t", sample = "b") shouldBe "TTTTTTT"
        }
    }

    @Test
    fun `getSequence slice of length 1 at every position`() {
        AgcArchive.open(fixturePath).use { archive ->
            val fullSeq = "AGCTAGCTAGCTAGCT"
            for (i in fullSeq.indices) {
                val base = archive.getSequence("chr1", start = i, end = i, sample = "ref")
                base shouldBe fullSeq[i].toString()
            }
        }
    }

    @Test
    fun `getSequence slice covers entire contig`() {
        AgcArchive.open(fixturePath).use { archive ->
            val full = archive.getSequence("chr1", sample = "ref")
            val sliced = archive.getSequence("chr1", start = 0, end = 15, sample = "ref")
            sliced shouldBe full
        }
    }

    @Test
    fun `contigLength for ambiguous contig name without sample throws`() {
        AgcArchive.open(fixturePath).use { archive ->
            shouldThrow<AgcNativeException> {
                archive.contigLength("chr1")
            }
        }
    }

    @Test
    fun `listContigs with null aggregates all samples`() {
        AgcArchive.open(fixturePath).use { archive ->
            val all = archive.listContigs(null)
            val expected = archive.listSamples().flatMap { archive.listContigs(it) }
            all shouldBe expected
        }
    }
}
