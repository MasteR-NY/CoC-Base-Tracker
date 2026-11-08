package com.villageclock.app;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.nio.file.Files;
import java.util.Arrays;
import static org.junit.Assert.*;

public class VillageArchiveTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void multipleAccountsStaySeparateAcrossRestart() throws Exception {
        java.io.File folder = temporary.newFolder();
        VillageArchive archive = new VillageArchive(folder);
        archive.write("#ABC123", "first account"); archive.write("#DEF456", "second account");
        VillageArchive restarted = new VillageArchive(folder);
        assertEquals("first account", restarted.read("#ABC123")); assertEquals("second account", restarted.read("#DEF456"));
        assertEquals(Arrays.asList("#ABC123", "#DEF456"), restarted.tags());
    }
    @Test public void updatingOneAccountDoesNotReplaceAnother() throws Exception {
        VillageArchive archive = new VillageArchive(temporary.newFolder());
        archive.write("#ABC123", "old"); archive.write("#DEF456", "keep"); archive.write("#ABC123", "new");
        assertEquals("new", archive.read("#ABC123")); assertEquals("keep", archive.read("#DEF456"));
    }
    @Test public void deletingOneAccountKeepsOtherAccounts() throws Exception {
        VillageArchive archive = new VillageArchive(temporary.newFolder());
        archive.write("#ABC123", "one"); archive.write("#DEF456", "two"); archive.remove("#ABC123");
        assertEquals(Arrays.asList("#DEF456"), archive.tags()); assertEquals("two", archive.read("#DEF456"));
    }
    @Test public void tagsAreCanonicalAndCannotEscapeStorage() throws Exception {
        VillageArchive archive = new VillageArchive(temporary.newFolder());
        archive.write("#abc123", "one"); assertEquals("one", archive.read("#ABC123"));
        for (String bad : Arrays.asList("../secret", "#../../xx", "#a/b", "", "#A")) {
            try { archive.write(bad, "x"); fail("Accepted " + bad); } catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void oversizedReplacementKeepsPreviousExport() throws Exception {
        VillageArchive archive = new VillageArchive(temporary.newFolder()); archive.write("#ABC123", "keep");
        try { archive.write("#ABC123", "x".repeat(ExportParser.MAX_CHARS + 1)); fail(); } catch (java.io.IOException expected) { }
        assertEquals("keep", archive.read("#ABC123"));
    }
    @Test public void partialTemporaryFilesAreNotAccounts() throws Exception {
        java.io.File folder = temporary.newFolder(); VillageArchive archive = new VillageArchive(folder);
        Files.write(new java.io.File(folder, "ABC123.json.tmp").toPath(), new byte[]{1});
        assertTrue(archive.tags().isEmpty()); archive.write("#ABC123", "complete");
        assertEquals(Arrays.asList("#ABC123"), archive.tags()); assertFalse(new java.io.File(folder, "ABC123.json.tmp").exists());
    }
}
