package com.dlfsystems.server

import kotlinx.serialization.Serializable

@Serializable
enum class Preposition(val strings: List<String>) {
    WITH(listOf("with", "using")),
    AT(listOf("at", "to")),
    IN_FRONT_OF(listOf("in front of", "before")),
    IN(listOf("in", "inside", "into")),
    ON(listOf("on top of", "onto", "on", "upon")),
    FROM(listOf("out of", "from inside", "from")),
    OVER(listOf("over")),
    THROUGH(listOf("through", "thru")),
    UNDER(listOf("under", "underneath", "beneath")),
    BEHIND(listOf("behind", "after")),
    BESIDE(listOf("beside")),
    FOR(listOf("for", "about")),
    IS(listOf("is")),
    AS(listOf("as")),
    OFF(listOf("off of", "off")),
}
