package com.dlfsystems.util

// Does string match pat*tern?
fun String.matchesWildcard(pattern: String): Boolean {
    if (this == pattern) return true
    if (pattern == "*") return true
    val i = pattern.indexOf('*')
    if (i == -1) return false
    val prefix = pattern.substring(0, i)
    val suffix = pattern.substring(i + 1)
    if (this.length < prefix.length) return false
    if (this.substring(0, prefix.length) != prefix) return false
    val mySuffix = this.substring(prefix.length)
    if (mySuffix == suffix.substring(0, mySuffix.length)) return true
    return false
}
