package com.rajaei.codexinspector.cli;

import com.rajaei.codexinspector.core.service.DefaultSnapshotService;
import com.rajaei.codexinspector.core.util.JsonSupport;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.util.concurrent.Callable;

@Command(name = "snapshot", description = "Create a deterministic JSON snapshot of a Codex state directory.")
public final class SnapshotCommand implements Callable<Integer> {
    @Option(names = "--source", required = true, description = "Source directory, for example ~/.codex")
    String source;

    @Option(names = "--output", required = true, description = "Output snapshot JSON path")
    String output;

    @Override
    public Integer call() throws Exception {
        var snapshot = new DefaultSnapshotService().create(PathOptions.expand(source));
        var outputPath = PathOptions.expand(output).toAbsolutePath().normalize();
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }
        JsonSupport.mapper().writeValue(outputPath.toFile(), snapshot);
        System.out.printf("Snapshot written: %s%n", outputPath);
        System.out.printf("Files: %d, directories: %d, bytes: %d, root checksum: %s%n",
                snapshot.totalFiles(), snapshot.totalDirectories(), snapshot.totalSizeBytes(), snapshot.rootChecksum());
        return 0;
    }
}
