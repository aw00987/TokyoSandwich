package com.github.aw00987.tokyosandwich.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class JwtTest {
    private fun jwt(payload: String) =
        "header.${
            Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
        }.signature"

    @Test
    fun readsStringClaims() {
        val token = jwt("""{"email":"user@example.com","exp":1000}""")
        assertEquals("user@example.com", jwtStringClaim(token, "email"))
        assertNull(jwtStringClaim(token, "name"))
        assertNull(jwtStringClaim("not-a-jwt", "email"))
    }
}
