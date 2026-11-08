package com.villageclock.app

/** Plain-text account labels; preserve international names and complete emoji. */
object AccountNames {
    @JvmStatic fun clean(value: Any?): String {
        if (value !is String) return ""
        val result = StringBuilder()
        var count = 0
        for (original in value.codePoints().toArray()) {
            val cp = if (Character.isWhitespace(original)) 32 else original
            if (Character.isISOControl(cp) || cp in 0x202A..0x202E || cp in 0x2066..0x2069 || cp in 0xD800..0xDFFF) continue
            if (cp == 32 && (result.isEmpty() || result.last() == ' ')) continue
            if (count++ == 32) break
            result.appendCodePoint(cp)
        }
        return result.toString().trim()
    }
}
