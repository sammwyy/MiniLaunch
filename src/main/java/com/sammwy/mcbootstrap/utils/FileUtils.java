package com.sammwy.mcbootstrap.utils;

import java.io.File;
import java.nio.file.Path;

public class FileUtils {
    public static void ensureFile(File file) {
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void ensureFile(Path path) {
        ensureFile(path.toFile());
    }
}
