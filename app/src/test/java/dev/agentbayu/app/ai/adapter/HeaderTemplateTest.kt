package dev.agentbayu.app.ai.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeaderTemplateTest {

    @Test
    fun hexKeepsLeadingZeroesAndLowerCase() {
        assertEquals("000f10ff", hexOf(byteArrayOf(0, 15, 16, -1)))
        assertEquals("", hexOf(ByteArray(0)))
        assertEquals(64, hexOf(sha256("agent-bayu")).length)
    }

    @Test
    fun stableTokensRepeatForTheSameSeed() {
        val first = stableHexToken("header:session:conn-1")
        val second = stableHexToken("header:session:conn-1")

        assertEquals(first, second)
        assertEquals(TOKEN_LENGTH, first.length)
        assertTrue(HEX.matches(first))
        assertFalse(first == stableHexToken("header:session:conn-2"))
    }

    @Test
    fun randomTokensDifferPerCall() {
        val first = randomHexToken()
        val second = randomHexToken()

        assertEquals(TOKEN_LENGTH, first.length)
        assertTrue(HEX.matches(first))
        assertFalse(first == second)
    }

    @Test
    fun theSessionTokenFollowsTheConnectionAcrossRequests() {
        val first = HeaderTokens("conn-1").expand("ses_{session}")
        val second = HeaderTokens("conn-1").expand("ses_{session}")
        val other = HeaderTokens("conn-2").expand("ses_{session}")

        assertEquals(first, second)
        assertFalse(first == other)
        assertTrue(first.startsWith("ses_"))
        assertTrue(HEX.matches(first.removePrefix("ses_")))
    }

    @Test
    fun theRequestTokenIsFreshPerInstanceAndStableInsideIt() {
        val tokens = HeaderTokens("conn-1")

        val first = tokens.expand("msg_{request}")
        assertEquals(first, tokens.expand("msg_{request}"))
        assertFalse(first == HeaderTokens("conn-1").expand("msg_{request}"))
        assertTrue(HEX.matches(first.removePrefix("msg_")))
    }

    @Test
    fun theUuidTokenIsAFreshVersionFourValuePerInstance() {
        val tokens = HeaderTokens("conn-1")

        val first = tokens.expand("{uuid}")
        assertEquals(first, tokens.expand("{uuid}"))
        assertTrue(UUID_SHAPE.matches(first))
        assertFalse(first == HeaderTokens("conn-1").expand("{uuid}"))
    }

    @Test
    fun everyTokenInOneValueIsReplaced() {
        val expanded = HeaderTokens("conn-1").expand("{session}/{request}/{uuid}")
        val parts = expanded.split("/")

        assertEquals(3, parts.size)
        assertTrue(HEX.matches(parts[0]))
        assertTrue(HEX.matches(parts[1]))
        assertTrue(UUID_SHAPE.matches(parts[2]))
    }

    @Test
    fun plainValuesPassThroughUntouched() {
        val tokens = HeaderTokens("conn-1")
        val agent = "antigravity/ide/2.11.0 darwin/arm64"

        assertEquals("desktop", tokens.expand("desktop"))
        assertEquals("", tokens.expand(""))
        assertEquals(agent, tokens.expand(agent))
        assertEquals("{unknown}", tokens.expand("{unknown}"))
    }

    private companion object {
        const val TOKEN_LENGTH = 32
        val HEX = Regex("^[0-9a-f]+$")
        val UUID_SHAPE =
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
    }
}
