package com.renovator.blueprint.importer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;

/** Loads any supported blueprint file by extension. */
public final class Importers {
    private Importers() {}

    public static final Set<String> EXTENSIONS = Set.of("nbt", "schem", "schematic", "litematic");
    private static final long MAX_NBT_BYTES = 256L * 1024 * 1024;

    public static String extension(Path file) {
        String n = file.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return dot < 0 ? "" : n.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean supported(Path file) {
        return EXTENSIONS.contains(extension(file));
    }

    public static String baseName(Path file) {
        return file.getFileName().toString().replaceFirst("\\.[^.]+$", "");
    }

    public static ImportResult load(Path file, HolderGetter<Block> blocks) throws IOException {
        CompoundTag root;
        try (InputStream in = Files.newInputStream(file)) {
            root = NbtIo.readCompressed(in, NbtAccounter.create(MAX_NBT_BYTES));
        }
        return load(root, extension(file), baseName(file), blocks);
    }

    public static ImportResult load(CompoundTag root, String extension, String name, HolderGetter<Block> blocks) throws IOException {
        return switch (extension) {
            case "nbt" -> VanillaNbtImporter.read(root, blocks, name);
            case "litematic" -> LitematicImporter.read(root, blocks, name);
            case "schem", "schematic" -> {
                if (root.contains("Materials", Tag.TAG_STRING) && root.contains("Blocks", Tag.TAG_BYTE_ARRAY)) {
                    throw new IOException("legacy MCEdit schematic (before 1.13) is not supported");
                }
                yield SpongeSchemImporter.read(root, blocks, name);
            }
            default -> throw new IOException("unsupported file type ." + extension);
        };
    }
}
