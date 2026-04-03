package org.btmonier.agckt

import com.sun.jna.Memory
import com.sun.jna.ptr.IntByReference
import org.btmonier.agckt.internal.AgcLibrary
import org.btmonier.agckt.internal.CLibrary
import org.btmonier.agckt.internal.NativeLoader
import org.btmonier.agckt.internal.toStringList
import java.io.Closeable
import java.lang.ref.Cleaner
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A read-only handle to an AGC (Assembled Genomes Compressor) archive.
 *
 * Implements [Closeable] so it can be used with Kotlin's `use {}` block.
 * Instances are obtained via [AgcArchive.open].
 *
 * This class is **not** thread-safe. Concurrent access to the same archive
 * handle must be externally synchronized, or callers should open separate
 * handles per thread.
 */
class AgcArchive private constructor(private val handle: com.sun.jna.Pointer, private val lib: AgcLibrary) :
    Closeable {

    private val closed = AtomicBoolean(false)

    private val cleanable = CLEANER.register(this, CleanAction(handle, lib, closed))

    private class CleanAction(
        private val handle: com.sun.jna.Pointer,
        private val lib: AgcLibrary,
        private val closed: AtomicBoolean,
    ) : Runnable {
        override fun run() {
            if (closed.compareAndSet(false, true)) {
                lib.agc_close(handle)
            }
        }
    }

    companion object {
        private val CLEANER = Cleaner.create()

        /**
         * Open an AGC archive for reading.
         *
         * @param path path to the `.agc` file
         * @param prefetch if `true`, preload the entire file into memory
         *   (faster when many queries will be issued)
         * @return an open [AgcArchive] handle
         * @throws AgcException if the archive cannot be opened
         */
        fun open(path: Path, prefetch: Boolean = true): AgcArchive {
            val lib = NativeLoader.load()
            val handle = lib.agc_open(path.toAbsolutePath().toString(), if (prefetch) 1 else 0)
                ?: throw AgcException("Failed to open AGC archive: $path")
            return AgcArchive(handle, lib)
        }
    }

    /** Number of samples in the archive. */
    val sampleCount: Int
        get() = checkOpen { lib.agc_n_sample(handle) }

    /**
     * The reference sample name, or `null` if none is set.
     *
     * The returned string is copied from native memory; the native
     * allocation is freed before this property returns.
     */
    val referenceSample: String?
        get() = checkOpen {
            val ptr = lib.agc_reference_sample(handle) ?: return@checkOpen null
            try {
                ptr.getString(0)
            } finally {
                CLibrary.INSTANCE.free(ptr)
            }
        }

    /**
     * List all sample names in the archive.
     *
     * @return an immutable list of sample names
     */
    fun listSamples(): List<String> = checkOpen {
        val count = IntByReference()
        val listPtr = lib.agc_list_sample(handle, count)
            ?: throw AgcNativeException("agc_list_sample returned null")
        try {
            listPtr.toStringList(count.value)
        } finally {
            lib.agc_list_destroy(listPtr)
        }
    }

    /**
     * Count the number of contigs for a given sample.
     *
     * @param sample the sample name
     * @return number of contigs
     */
    fun contigCount(sample: String): Int = checkOpen {
        lib.agc_n_ctg(handle, sample)
    }

    /**
     * List contig names for a sample.
     *
     * When [sample] is `null`, returns the union of contigs across all samples.
     *
     * @param sample sample name, or `null` to list contigs across all samples
     * @return an immutable list of contig names
     */
    fun listContigs(sample: String? = null): List<String> = checkOpen {
        if (sample == null) {
            listSamples().flatMap { listContigsForSample(it) }
        } else {
            listContigsForSample(sample)
        }
    }

    private fun listContigsForSample(sample: String): List<String> {
        val count = IntByReference()
        val listPtr = lib.agc_list_ctg(handle, sample, count)
            ?: throw AgcNativeException("agc_list_ctg returned null for sample='$sample'")
        return try {
            listPtr.toStringList(count.value)
        } finally {
            lib.agc_list_destroy(listPtr)
        }
    }

    /**
     * Get the length of a contig.
     *
     * @param contig contig name
     * @param sample sample name, or `null` if the contig name is unique
     * @return the contig length in bases
     * @throws AgcNativeException if the contig is not found or the name is ambiguous
     */
    fun contigLength(contig: String, sample: String? = null): Int = checkOpen {
        val len = lib.agc_get_ctg_len(handle, sample, contig)
        if (len < 0) {
            throw AgcNativeException(
                "agc_get_ctg_len failed for contig='$contig', sample=${sample ?: "(null)"} (returned $len)",
            )
        }
        len
    }

    /**
     * Extract the full sequence of a contig.
     *
     * @param contig contig name
     * @param sample sample name, or `null` if the contig name is unique
     * @return the nucleotide sequence as a [String]
     * @throws AgcNativeException if the contig is not found
     */
    fun getSequence(contig: String, sample: String? = null): String = checkOpen {
        val len = contigLength(contig, sample)
        if (len == 0) return@checkOpen ""
        getSequence(contig, 0, len - 1, sample)
    }

    /**
     * Extract a slice of a contig sequence.
     *
     * Both [start] and [end] are 0-based and **inclusive**.
     *
     * @param contig contig name
     * @param start start offset (inclusive, 0-based)
     * @param end end offset (inclusive, 0-based)
     * @param sample sample name, or `null` if the contig name is unique
     * @return the nucleotide subsequence as a [String]
     * @throws AgcNativeException if the extraction fails
     */
    fun getSequence(contig: String, start: Int, end: Int, sample: String? = null): String = checkOpen {
        require(start >= 0) { "start must be >= 0, was $start" }
        require(end >= start) { "end must be >= start, was end=$end start=$start" }
        val bufSize = (end - start + 1).toLong() + 1 // +1 for NUL terminator
        val buf = Memory(bufSize)
        val result = lib.agc_get_ctg_seq(handle, sample, contig, start, end, buf)
        if (result < 0) {
            throw AgcNativeException(
                "agc_get_ctg_seq failed for contig='$contig', range=[$start,$end], " +
                    "sample=${sample ?: "(null)"} (returned $result)",
            )
        }
        buf.getString(0)
    }

    /**
     * Close this archive handle, releasing native resources.
     *
     * Calling `close()` multiple times is safe (idempotent).
     * After closing, any method call will throw [IllegalStateException].
     */
    override fun close() {
        if (closed.compareAndSet(false, true)) {
            lib.agc_close(handle)
            cleanable.clean()
        }
    }

    private inline fun <T> checkOpen(block: () -> T): T {
        check(!closed.get()) { "AgcArchive is already closed" }
        return block()
    }
}
