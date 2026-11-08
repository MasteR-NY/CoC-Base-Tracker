package com.villageclock.app;

/** Small, plain-text account labels; preserve international names and complete emoji. */
public final class AccountNames {
    static String clean(Object value) {
        if (!(value instanceof String)) return "";
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (int cp : ((String)value).codePoints().toArray()) {
            if (Character.isWhitespace(cp)) cp = ' ';
            if (Character.isISOControl(cp) || cp >= 0x202A && cp <= 0x202E || cp >= 0x2066 && cp <= 0x2069
                    || cp >= 0xD800 && cp <= 0xDFFF) continue;
            if (cp == ' ' && (out.length() == 0 || out.charAt(out.length()-1) == ' ')) continue;
            if (count++ == 32) break;
            out.appendCodePoint(cp);
        }
        return out.toString().trim();
    }
    private AccountNames() { }
}
