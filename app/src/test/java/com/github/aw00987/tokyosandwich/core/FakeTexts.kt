package com.github.aw00987.tokyosandwich.core

/** Texts for tests: the resource ID and the arguments, so a test can tell which text was used with which values. */
object FakeTexts : Texts {
    override fun get(id: Int, vararg args: Any): String =
        (listOf("#$id") + args.map { it.toString() }).joinToString("|")
}
