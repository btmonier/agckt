package org.btmonier.agckt.internal

import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder

/**
 * Raw JNA binding tests exercising every AGC C API function
 * directly against the toy_ex.agc fixture.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class AgcLibraryTest {

    private lateinit var lib: AgcLibrary
    private lateinit var handle: Pointer

    private val fixturePath: String by lazy {
        val url = javaClass.classLoader.getResource("fixtures/toy_ex.agc")
        assumeTrue(url != null, "toy_ex.agc fixture not found on classpath")
        java.io.File(url!!.toURI()).absolutePath
    }

    @BeforeAll
    fun setUp() {
        lib = try {
            NativeLoader.load()
        } catch (e: Exception) {
            assumeTrue(false, "Native library not available: ${e.message}")
            throw e
        }
        val ptr = lib.agc_open(fixturePath, 1)
        assumeTrue(ptr != null, "agc_open returned null for fixture")
        handle = ptr!!
    }

    @AfterAll
    fun tearDown() {
        if (::handle.isInitialized) {
            lib.agc_close(handle) shouldBe 0
        }
    }

    @Test
    @Order(1)
    fun `agc_n_sample returns correct count`() {
        lib.agc_n_sample(handle) shouldBe 4
    }

    @Test
    @Order(2)
    fun `agc_list_sample returns all sample names`() {
        val count = IntByReference()
        val listPtr = lib.agc_list_sample(handle, count)
        listPtr.shouldNotBeNull()
        try {
            count.value shouldBe 4
            val samples = listPtr.toStringList(count.value)
            samples shouldContainExactlyInAnyOrder listOf("ref", "a", "b", "c")
        } finally {
            lib.agc_list_destroy(listPtr)
        }
    }

    @Test
    @Order(3)
    fun `agc_n_ctg returns correct count per sample`() {
        lib.agc_n_ctg(handle, "ref") shouldBe 4
        lib.agc_n_ctg(handle, "a") shouldBe 2
        lib.agc_n_ctg(handle, "b") shouldBe 4
        lib.agc_n_ctg(handle, "c") shouldBe 3
    }

    @Test
    @Order(4)
    fun `agc_list_ctg returns contig names for a sample`() {
        val count = IntByReference()
        val listPtr = lib.agc_list_ctg(handle, "ref", count)
        listPtr.shouldNotBeNull()
        try {
            count.value shouldBe 4
            val contigs = listPtr.toStringList(count.value)
            contigs shouldContainExactlyInAnyOrder listOf("chr1", "chr2", "chr3", "seq")
        } finally {
            lib.agc_list_destroy(listPtr)
        }
    }

    @Test
    @Order(5)
    fun `agc_get_ctg_len returns correct lengths`() {
        lib.agc_get_ctg_len(handle, "ref", "chr1") shouldBe 16
        lib.agc_get_ctg_len(handle, "ref", "chr2") shouldBe 15
        lib.agc_get_ctg_len(handle, "ref", "chr3") shouldBe 14
        lib.agc_get_ctg_len(handle, "ref", "seq") shouldBe 10
        lib.agc_get_ctg_len(handle, "a", "chr1a") shouldBe 13
        lib.agc_get_ctg_len(handle, "c", "3") shouldBe 16
    }

    @Test
    @Order(6)
    fun `agc_get_ctg_seq extracts full sequence`() {
        val len = lib.agc_get_ctg_len(handle, "ref", "chr1")
        len shouldBe 16
        val buf = Memory((len + 1).toLong())
        val result = lib.agc_get_ctg_seq(handle, "ref", "chr1", 0, len - 1, buf)
        result shouldBeGreaterThan 0
        buf.getString(0) shouldBe "AGCTAGCTAGCTAGCT"
    }

    @Test
    @Order(7)
    fun `agc_get_ctg_seq extracts a subsequence slice`() {
        val buf = Memory(5)
        val result = lib.agc_get_ctg_seq(handle, "ref", "chr1", 0, 3, buf)
        result shouldBeGreaterThan 0
        buf.getString(0) shouldBe "AGCT"
    }

    @Test
    @Order(8)
    fun `agc_get_ctg_seq with null sample works when contig is unique`() {
        val len = lib.agc_get_ctg_len(handle, "a", "chr1a")
        val buf = Memory((len + 1).toLong())
        val result = lib.agc_get_ctg_seq(handle, null, "chr1a", 0, len - 1, buf)
        result shouldBeGreaterThan 0
        buf.getString(0) shouldBe "CTGAGCTGACTGA"
    }

    @Test
    @Order(9)
    fun `agc_reference_sample returns a non-empty string`() {
        val ptr = lib.agc_reference_sample(handle)
        ptr.shouldNotBeNull()
        val refSample = ptr.getString(0)
        refSample.shouldNotBeEmpty()
        CLibrary.INSTANCE.free(ptr)
    }

    @Test
    @Order(10)
    fun `agc_get_ctg_len returns negative for nonexistent contig`() {
        val result = lib.agc_get_ctg_len(handle, "ref", "nonexistent_contig")
        result shouldBe -1
    }
}
