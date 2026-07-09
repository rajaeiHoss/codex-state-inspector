package com.rajaei.codexinspector.cli;

import com.rajaei.codexinspector.core.service.ImportantCodexPath;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

@Command(name = "watch", mixinStandardHelpOptions = true, description = "Watch a Codex state directory for live filesystem changes.")
public final class WatchCommand implements Callable<Integer> {
    @Option(names = "--source", required = true, description = "Codex state directory, for example ~/.codex")
    String source;

    @Option(names = "--recursive", defaultValue = "true", description = "Register subdirectories recursively.")
    boolean recursive;

    @Option(names = "--max-events", description = "Stop after this many events. Useful for tests and scripts.")
    Integer maxEvents;

    @Option(names = "--timeout-seconds", description = "Stop after this many seconds even if no events arrive.")
    Long timeoutSeconds;

    @Override
    public Integer call() throws Exception {
        Path root = PathOptions.expand(source).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            System.out.println("Source is not a directory: " + root);
            return 2;
        }
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            Map<WatchKey, Path> keys = new HashMap<>();
            register(root, watchService, keys);
            if (recursive) {
                try (var stream = Files.walk(root)) {
                    stream.filter(Files::isDirectory)
                            .filter(path -> !path.equals(root))
                            .forEach(path -> registerQuietly(path, watchService, keys));
                }
            }

            System.out.printf("Watching %s (%d directories)%n", root, keys.size());
            int events = 0;
            Instant deadline = timeoutSeconds == null ? null : Instant.now().plus(Duration.ofSeconds(timeoutSeconds));
            while (true) {
                if (maxEvents != null && events >= maxEvents) {
                    return 0;
                }
                if (deadline != null && Instant.now().isAfter(deadline)) {
                    return 0;
                }

                WatchKey key = poll(watchService, deadline);
                if (key == null) {
                    continue;
                }
                Path directory = keys.get(key);
                if (directory == null) {
                    key.reset();
                    continue;
                }
                for (WatchEvent<?> event : key.pollEvents()) {
                    Path changed = directory.resolve((Path) event.context()).normalize();
                    Path relative = root.relativize(changed);
                    System.out.printf("%s %s%s%n", event.kind().name(), relative, ImportantCodexPath.marker(relative));
                    events++;
                    if (recursive && event.kind() == ENTRY_CREATE && Files.isDirectory(changed)) {
                        registerQuietly(changed, watchService, keys);
                    }
                    if (maxEvents != null && events >= maxEvents) {
                        break;
                    }
                }
                if (!key.reset()) {
                    keys.remove(key);
                }
                if (keys.isEmpty()) {
                    return 0;
                }
            }
        }
    }

    private static WatchKey poll(WatchService watchService, Instant deadline) throws InterruptedException {
        if (deadline == null) {
            return watchService.take();
        }
        long millis = Math.max(1L, Duration.between(Instant.now(), deadline).toMillis());
        return watchService.poll(millis, TimeUnit.MILLISECONDS);
    }

    private static void register(Path directory, WatchService watchService, Map<WatchKey, Path> keys) throws IOException {
        WatchKey key = directory.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
        keys.put(key, directory);
    }

    private static void registerQuietly(Path directory, WatchService watchService, Map<WatchKey, Path> keys) {
        try {
            register(directory, watchService, keys);
        } catch (IOException ignored) {
            // Some directories can disappear while the watcher is starting.
        }
    }
}
