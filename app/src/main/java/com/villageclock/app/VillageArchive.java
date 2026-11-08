package com.villageclock.app;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Bounded, atomically replaced exports; one private file per player tag. */
public final class VillageArchive {
    private final File directory;
    public VillageArchive(File directory) { this.directory = directory; }
    public static String canonical(String tag) {
        if (tag == null || !tag.matches("#[A-Za-z0-9]{2,16}")) throw new IllegalArgumentException("Invalid village tag");
        return tag.toUpperCase(Locale.ROOT);
    }
    private File file(String tag) { return new File(directory, canonical(tag).substring(1) + ".json"); }
    public synchronized String read(String tag) throws IOException {
        File target = file(tag);
        if (target.length() > ExportParser.MAX_CHARS * 4L) throw new IOException("Export is too large");
        return new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8);
    }
    public synchronized void write(String tag, String text) throws IOException {
        File target = file(tag);
        if (text == null || text.length() > ExportParser.MAX_CHARS) throw new IOException("Export is too large");
        Files.createDirectories(directory.toPath());
        File temporary = new File(directory, target.getName() + ".tmp");
        try {
            Files.write(temporary.toPath(), text.getBytes(StandardCharsets.UTF_8));
            try { Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary.toPath()); }
    }
    public synchronized void remove(String tag) throws IOException { Files.deleteIfExists(file(tag).toPath()); }
    public synchronized List<String> tags() {
        ArrayList<String> tags = new ArrayList<>();
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) {
            String name = file.getName();
            if (file.isFile() && name.matches("[A-Z0-9]{2,16}\\.json")) tags.add("#" + name.substring(0, name.length() - 5));
        }
        Collections.sort(tags);
        return tags;
    }
}
