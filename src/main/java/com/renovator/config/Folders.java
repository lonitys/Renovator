package com.renovator.config;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;

/**
 * Renovator's folders on disk. Earlier builds were called Stilwerk and used German folder names; when the new
 * folder does not exist yet, the first old one found is moved over once.
 */
public final class Folders {
    private Folders() {}

    private static final Logger LOG = LogUtils.getLogger();

    /** {@code base/name}; when it does not exist, the first existing {@code legacy} folder is renamed to it first. */
    public static Path resolve(Path base, String name, Path... legacy) {
        Path dir = base.resolve(name);
        if (Files.exists(dir)) return dir;
        for (Path old : legacy) {
            if (!Files.isDirectory(old)) continue;
            try {
                Files.createDirectories(base);
                Files.move(old, dir);
                LOG.info("Renovator: moved {} to {}", old, dir);
                return dir;
            } catch (IOException e) {
                LOG.warn("Renovator: cannot move {} to {}, using the old folder", old, dir, e);
                return old;
            }
        }
        return dir;
    }
}
