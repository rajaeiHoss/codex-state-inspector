package com.rajaei.codexinspector.core.service;

import java.nio.file.Path;
import java.util.Set;

public final class ImportantCodexPath {
    private static final Set<String> IMPORTANT_NAMES = Set.of(
            "state_5.sqlite",
            "state_5.sqlite-wal",
            "state_5.sqlite-shm",
            "session_index.jsonl",
            ".codex-global-state.json",
            "models_cache.json",
            "logs_2.sqlite",
            "sessions",
            "archived_sessions"
    );

    private ImportantCodexPath() {
    }

    public static boolean isImportant(Path path) {
        for (Path part : path) {
            if (IMPORTANT_NAMES.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    public static String marker(Path path) {
        return isImportant(path) ? " *" : "";
    }
}
