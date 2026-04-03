@file:Suppress("ktlint:standard:function-naming")

package org.btmonier.agckt.internal

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

/**
 * Minimal libc bindings for memory deallocation.
 *
 * Used to free `char*` strings allocated by AGC's C API (e.g., `agc_reference_sample`).
 * The upstream AGC implementation uses `malloc`/`free` for these allocations.
 */
internal interface CLibrary : Library {
    fun free(ptr: Pointer)

    companion object {
        val INSTANCE: CLibrary = Native.load(
            com.sun.jna.Platform.C_LIBRARY_NAME,
            CLibrary::class.java,
        )
    }
}
