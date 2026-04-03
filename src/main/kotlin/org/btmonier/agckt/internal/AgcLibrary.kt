@file:Suppress("ktlint:standard:function-naming")

package org.btmonier.agckt.internal

import com.sun.jna.Library
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference

/**
 * JNA interface mapping the AGC C API functions from `agc-api.h`.
 *
 * Function names use underscores to match C symbol names exactly,
 * which is required for JNA to resolve them at runtime.
 *
 * All pointers returned by `agc_list_*` and `agc_reference_sample` are
 * **owned by the caller** and must be freed with the corresponding
 * destroy function. See ownership rules in the architecture docs.
 */
internal interface AgcLibrary : Library {

    fun agc_open(fn: String, prefetching: Int): Pointer?

    fun agc_close(agc: Pointer): Int

    fun agc_n_sample(agc: Pointer): Int

    fun agc_n_ctg(agc: Pointer, sample: String?): Int

    fun agc_get_ctg_len(agc: Pointer, sample: String?, name: String): Int

    fun agc_get_ctg_seq(agc: Pointer, sample: String?, name: String, start: Int, end: Int, buf: Pointer): Int

    fun agc_list_sample(agc: Pointer, nSample: IntByReference): Pointer?

    fun agc_list_ctg(agc: Pointer, sample: String?, nCtg: IntByReference): Pointer?

    fun agc_list_destroy(list: Pointer): Int

    fun agc_reference_sample(agc: Pointer): Pointer?
}
