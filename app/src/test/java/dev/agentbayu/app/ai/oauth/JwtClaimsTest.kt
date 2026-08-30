package dev.agentbayu.app.ai.oauth

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JwtClaimsTest {

    private fun encode(payload: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(payload.toByteArray(Charsets.UTF_8))

    private fun token(payload: String): String = "e30." + encode(payload) + ".signature"

    @Test
    fun `a nested claim field is read from the payload`() {
        val jwt = token(
            "{\"https://api.openai.com/auth\":{\"chatgpt_account_id\":\"acc-42\"}}"
        )

        assertEquals(
            "acc-42",
            JwtClaims.claim(jwt, "https://api.openai.com/auth", "chatgpt_account_id")
        )
    }

    @Test
    fun `a top level claim is read when no field is given`() {
        val jwt = token("{\"email\":\"owner@example.test\"}")

        assertEquals("owner@example.test", JwtClaims.claim(jwt, "email", null))
        assertEquals("owner@example.test", JwtClaims.claim(jwt, "email", " "))
    }

    @Test
    fun `a token without padding still decodes`() {
        val jwt = token("{\"a\":\"1234567\"}")

        assertEquals("1234567", JwtClaims.claim(jwt, "a", null))
    }

    @Test
    fun `a missing claim or field returns null`() {
        val jwt = token("{\"https://api.openai.com/auth\":{\"other\":\"x\"}}")

        assertNull(JwtClaims.claim(jwt, "https://api.openai.com/auth", "chatgpt_account_id"))
        assertNull(JwtClaims.claim(jwt, "nope", "chatgpt_account_id"))
        assertNull(JwtClaims.claim(jwt, "nope", null))
    }

    @Test
    fun `a claim that is not an object returns null when a field is asked for`() {
        val jwt = token("{\"https://api.openai.com/auth\":\"plain\"}")

        assertNull(JwtClaims.claim(jwt, "https://api.openai.com/auth", "chatgpt_account_id"))
    }

    @Test
    fun `a broken token returns null without throwing`() {
        assertNull(JwtClaims.payload("not-a-jwt"))
        assertNull(JwtClaims.payload(""))
        assertNull(JwtClaims.payload("e30.."))
        assertNull(JwtClaims.payload("e30.###.sig"))
        assertNull(JwtClaims.payload("e30." + encode("not json") + ".sig"))
        assertNull(JwtClaims.claim("###", "email", null))
    }

    @Test
    fun `a two part token is enough`() {
        val jwt = "e30." + encode("{\"email\":\"owner@example.test\"}")

        assertEquals("owner@example.test", JwtClaims.claim(jwt, "email", null))
    }
}
