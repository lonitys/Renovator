package com.stilwerk.config;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;

/** Stilwerk's folders on disk. Early builds used German folder names; those are moved to the English ones once. */
public final class Folders {
    private Folders() {}

    private static final Logger LOG = LogUtils.getLogger();

    /** {@code base/name}; when only the old folder {@code base/legacy} exists, it is renamed first. */
    public static Path resolve(Path base, String name, String legacy) {
        Path dir = base.resolve(name);
        Path old = base.resolve(legacy);
        if (!Files.exists(dir) && Files.isDirectory(old)) {
            try {
                Files.move(old, dir);
                LOG.info("Stilwerk: moved {} to {}", old, dir);
            } catch (IOException e) {
                LOG.warn("Stilwerk: cannot move {} to {}, using the old folder", old, dir, e);
                return old;
            }
        }
        return dir;
    }
}
