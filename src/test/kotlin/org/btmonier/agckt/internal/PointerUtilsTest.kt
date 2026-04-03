package org.btmonier.agckt.internal

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PointerUtilsTest {

    @Test
    fun `toStringList converts char-star-star to list`() {
        val strings = listOf("alpha", "beta", "gamma")
        val nativeStrings = strings.map { s ->
            val mem = Memory((s.length + 1).toLong())
            mem.setString(0, s)
            mem
        }

        val ptrArray = Memory((Native.POINTER_SIZE * strings.size).toLong())
        nativeStrings.forEachIndexed { i, mem ->
            ptrArray.setPointer((i.toLong() * Native.POINTER_SIZE), mem)
        }

        val result = (ptrArray as Pointer).toStringList(strings.size)
        result shouldBe strings
    }

    @Test
    fun `toStringList with zero count returns empty list`() {
        val mem = Memory(8)
        val result = (mem as Pointer).toStringList(0)
        result shouldBe emptyList()
    }
}
