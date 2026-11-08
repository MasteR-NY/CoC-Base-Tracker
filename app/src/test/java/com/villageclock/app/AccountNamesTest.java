package com.villageclock.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class AccountNamesTest {
    @Test public void preservesInternationalNamesAndCompleteEmoji() {
        assertEquals("龍 Chief 👑", AccountNames.clean("  龍  Chief 👑  "));
        String longName="👑".repeat(40);String clean=AccountNames.clean(longName);
        assertEquals(32,clean.codePointCount(0,clean.length()));assertTrue(clean.endsWith("👑"));
    }
    @Test public void optionalExportNamesCannotBecomeJsonOrControlCharacters() throws Exception {
        long now=1700000000000L;
        String raw="{\"tag\":\"#NAME12\",\"timestamp\":"+now/1000+",\"name\":\"龍 Chief 👑\",\"buildings\":[]}";
        assertEquals("龍 Chief 👑",ExportParser.parse(raw,id->"",now).playerName);
        assertEquals("",ExportParser.parse(raw.replace("\"龍 Chief 👑\"","{\"bad\":true}"),id->"",now).playerName);
        assertEquals("Chief Name",AccountNames.clean("Chief\n\tName\u202E"));
    }
}
