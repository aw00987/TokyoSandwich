package com.github.aw00987.tokyosandwich.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.Base64

/** The claims of a JWT, decoded without checking the signature; null when [token] is not a readable JWT. */
fun jwtClaims(token: String): JsonObject? = runCatching {
    val payload = String(Base64.getUrlDecoder().decode(token.split('.')[1].trimEnd('=')))
    Json.parseToJsonElement(payload).jsonObject
}.getOrNull()

/** A string claim of [token], or null when it is missing or the token is not a readable JWT. */
fun jwtStringClaim(token: String, name: String): String? =
    runCatching { jwtClaims(token)?.get(name)?.jsonPrimitive?.contentOrNull }.getOrNull()

/** True if [token] is not a readable JWT or its `exp` claim is within [skewSeconds] of [nowSeconds]. */
fun isJwtExpired(token: String, nowSeconds: Long, skewSeconds: Long = 60): Boolean {
    val exp = runCatching { jwtClaims(token)?.get("exp")?.jsonPrimitive?.longOrNull }.getOrNull()
        ?: return true
    return nowSeconds >= exp - skewSeconds
}
