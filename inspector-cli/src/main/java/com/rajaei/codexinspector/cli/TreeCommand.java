package com.rajaei.codexinspector.cli;

import com.rajaei.codexinspector.core.domain.SnapshotDirectory;
import com.rajaei.codexinspector.core.domain.SnapshotFile;
import com.rajaei.codexinspector.core.service.DefaultSnapshotService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.Set;
import java.util.concurrent.Callable;

@Command(name = "tree", description = "Print a readable tree with size and checksum prefixes.")
public final class TreeCommand implements Callable<Integer> {
    private static final Set<String> IMPORTANT = Set.of(
            "state_5.sqlite", "state_5.sqlite-wal", "state_5.sqlite-shm", "session_index.jsonl",
            ".codex-global-state.json", "models_cache.json", "logs_2.sqlite", "sessions", "archived_sessions"
    );

    @Option(names = "--source", required = true)
    String source;

    @Option(names = "--depth", defaultValue = "3")
    int depth;

    @Override
    public Integer call() throws Exception {
        var snapshot = new DefaultSnapshotService().create(PathOptions.expand(source));
        System.out.printf("%s [%s]%n", snapshot.rootPath(), snapshot.rootChecksum().substring(0, 12));
        print(snapshot.tree(), 0);
        return 0;
    }

    private void print(SnapshotDirectory directory, int level) {
        if (level >= depth) {
            return;
        }
        for (SnapshotDirectory child : directory.directories()) {
            System.out.printf("%s%s/ [%s]%s%n", indent(level), name(child.path()), prefix(child.sha256()), marker(child.path()));
            print(child, level + 1);
        }
        for (SnapshotFile file : directory.files()) {
            System.out.printf("%s%s %dB [%s]%s%s%n", indent(level), name(file.path()), file.sizeBytes(), prefix(file.sha256()), marker(file.path()),
                    file.symlinkTarget() == null ? "" : " -> " + file.symlinkTarget());
        }
    }

    private static String indent(int level) {
        return "  ".repeat(level);
    }

    private static String prefix(String checksum) {
        return checksum.length() <= 12 ? checksum : checksum.substring(0, 12);
    }

    private static String name(String path) {
        int index = path.lastIndexOf('/');
        return index < 0 ? path : path.substring(index + 1);
    }

    private static String marker(String path) {
        return IMPORTANT.contains(name(path)) ? " *" : "";
    }
}
