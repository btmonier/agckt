package org.btmonier.agckt.internal

import com.sun.jna.Native
import com.sun.jna.Pointer

/**
 * Read a native `char**` (array of C string pointers) into a Kotlin list.
 *
 * The caller is responsible for freeing the native array with the
 * appropriate destroy function after this method returns.
 *
 * @param count number of strings in the array
 * @return a new list of copied [String] values
 */
internal fun Pointer.toStringList(count: Int): List<String> = (0 until count).map { i ->
    getPointer((i.toLong() * Native.POINTER_SIZE)).getString(0)
}
