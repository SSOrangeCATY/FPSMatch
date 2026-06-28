package com.tacz.guns.util;

import net.minecraft.resources.Identifier;

import java.io.File;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.BiConsumer;

public class TacPathVisitor extends SimpleFileVisitor<Path> {
    private final File root;
    private final String namespace;
    private final String suffix;
    private final BiConsumer<Identifier, Path> consumer;

    public TacPathVisitor(File root, String namespace, String suffix, BiConsumer<Identifier, Path> consumer) {
        this.root = root;
        this.namespace = namespace;
        this.suffix = suffix;
        this.consumer = consumer;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        if (file.toFile().getName().endsWith(suffix)) {
            String path = PathHandler.getPath(root.toPath(), file, suffix);
            Identifier id = Identifier.fromNamespaceAndPath(namespace, path);
            consumer.accept(id, file);
        }
        return FileVisitResult.CONTINUE;
    }
}
