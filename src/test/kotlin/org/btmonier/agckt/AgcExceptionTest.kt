package org.btmonier.agckt

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class AgcExceptionTest {

    @Test
    fun `AgcException carries message`() {
        val ex = AgcException("test error")
        ex.message shouldBe "test error"
    }

    @Test
    fun `AgcException carries cause`() {
        val cause = RuntimeException("root")
        val ex = AgcException("wrapper", cause)
        ex.cause shouldBe cause
    }

    @Test
    fun `AgcFileNotFoundException includes path in message`() {
        val ex = AgcFileNotFoundException("/tmp/missing.agc")
        ex.message!! shouldContain "/tmp/missing.agc"
        ex.shouldBeInstanceOf<AgcException>()
    }

    @Test
    fun `AgcNativeException is an AgcException`() {
        val ex = AgcNativeException("native failure")
        ex.shouldBeInstanceOf<AgcException>()
        ex.message shouldBe "native failure"
    }
}
