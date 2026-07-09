package com.rajaei.codexinspector.core.service;

import com.rajaei.codexinspector.core.domain.Snapshot;
import com.rajaei.codexinspector.core.domain.SnapshotDirectory;
import com.rajaei.codexinspector.core.domain.SnapshotFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class DefaultSnapshotService implements SnapshotService {
    private final ChecksumService checksumService;

    public DefaultSnapshotService() {
        this(new DefaultChecksumService());
    }

    public DefaultSnapshotService(ChecksumService checksumService) {
        this.checksumService = checksumService;
    }

    @Override
    public Snapshot create(Path source) throws IOException {
        Path root = source.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Source is not a directory: " + root);
        }
        Counter counter = new Counter();
        SnapshotDirectory tree = scanDirectory(root, root, counter);
        return new Snapshot(root.toString(), Instant.now(), counter.files, counter.directories, counter.bytes, tree.sha256(), tree);
    }

    private SnapshotDirectory scanDirectory(Path root, Path directory, Counter counter) throws IOException {
        counter.directories++;
        List<SnapshotDirectory> directories = new ArrayList<>();
        List<SnapshotFile> files = new ArrayList<>();

        try (var stream = Files.list(directory)) {
            List<Path> children = stream.sorted(Comparator.comparing(path -> root.relativize(path).toString())).toList();
            for (Path child : children) {
                String relative = normalize(root.relativize(child));
                if (Files.isSymbolicLink(child)) {
                    Path target = Files.readSymbolicLink(child);
                    String digest = checksumService.sha256("symlink\0" + relative + "\0" + target);
                    files.add(new SnapshotFile(relative, "symlink", 0L, digest, target.toString()));
                    counter.files++;
                } else if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    directories.add(scanDirectory(root, child, counter));
                } else if (Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)) {
                    long size = Files.size(child);
                    files.add(new SnapshotFile(relative, "file", size, checksumService.sha256(child), null));
                    counter.files++;
                    counter.bytes += size;
                } else {
                    String digest = checksumService.sha256("other\0" + relative);
                    files.add(new SnapshotFile(relative, "other", 0L, digest, null));
                    counter.files++;
                }
            }
        }

        String manifest = buildDirectoryManifest(normalize(root.relativize(directory)), directories, files);
        return new SnapshotDirectory(normalize(root.relativize(directory)), "directory", checksumService.sha256(manifest), directories, files);
    }

    private static String buildDirectoryManifest(String path, List<SnapshotDirectory> directories, List<SnapshotFile> files) {
        StringBuilder manifest = new StringBuilder("directory\0").append(path).append('\n');
        directories.stream()
                .sorted(Comparator.comparing(SnapshotDirectory::path))
                .forEach(directory -> manifest.append(directory.path()).append('\0').append(directory.type()).append('\0').append(directory.sha256()).append('\n'));
        files.stream()
                .sorted(Comparator.comparing(SnapshotFile::path))
                .forEach(file -> manifest.append(file.path()).append('\0').append(file.type()).append('\0').append(file.sha256()).append('\n'));
        return manifest.toString();
    }

    private static String normalize(Path path) {
        String value = path.toString().replace('\\', '/');
        return value.isBlank() ? "." : value;
    }

    private static final class Counter {
        long files;
        long directories;
        long bytes;
    }
}
