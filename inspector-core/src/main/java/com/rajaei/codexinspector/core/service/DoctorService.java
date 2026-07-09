package com.rajaei.codexinspector.core.service;

import com.rajaei.codexinspector.core.domain.DoctorReport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public class DoctorService {
    private final CodexStateService stateService = new CodexStateService();

    public DoctorReport check(Path source) {
        Path codexHome = source.toAbsolutePath().normalize();
        var checks = new ArrayList<String>();
        var warnings = new ArrayList<String>();
        var errors = new ArrayList<String>();
        long workspaces = 0;
        long activeWorkspaces = 0;
        long threads = 0;
        long archivedThreads = 0;
        long remoteAccounts = 0;
        long missingRollouts = 0;
        long missingWorkspacePaths = 0;

        requireDirectory(codexHome, checks, errors, "Codex source");
        requireFile(codexHome.resolve(DefaultJsonStateReader.GLOBAL_STATE_FILE), checks, errors, "global JSON state");
        requireFile(codexHome.resolve(DefaultSqliteStateReader.DB_FILE), checks, errors, "SQLite state database");
        detectFile(codexHome.resolve("state_5.sqlite-wal"), checks, warnings, "SQLite WAL");
        detectFile(codexHome.resolve("state_5.sqlite-shm"), checks, warnings, "SQLite SHM");
        requireFile(codexHome.resolve("session_index.jsonl"), checks, errors, "session index");
        requireDirectory(codexHome.resolve("sessions"), checks, errors, "sessions directory");
        requireDirectory(codexHome.resolve("archived_sessions"), checks, errors, "archived sessions directory");

        if (errors.isEmpty()) {
            try {
                var state = stateService.inspect(codexHome);
                workspaces = state.workspaces().size();
                activeWorkspaces = state.activeWorkspaceCount();
                threads = state.threads().size();
                archivedThreads = state.archivedThreadCount();
                remoteAccounts = state.remoteAccounts().size();
                missingWorkspacePaths = state.workspaces().stream().filter(workspace -> !workspace.exists()).count();
                for (var thread : state.threads()) {
                    if (thread.rolloutPath() != null && !thread.rolloutPath().isBlank()) {
                        Path rollout = Path.of(thread.rolloutPath());
                        if (!rollout.isAbsolute()) {
                            rollout = codexHome.resolve(rollout).normalize();
                        }
                        if (!Files.exists(rollout)) {
                            missingRollouts++;
                        }
                    }
                }
                if (missingRollouts > 0) {
                    errors.add("Missing rollout files: " + missingRollouts);
                }
                if (missingWorkspacePaths > 0) {
                    warnings.add("Missing workspace paths: " + missingWorkspacePaths);
                }
            } catch (Exception e) {
                errors.add("Unable to inspect Codex state: " + e.getMessage());
            }
        }

        return new DoctorReport(errors.isEmpty(), checks, warnings, errors, workspaces, activeWorkspaces, threads, archivedThreads, remoteAccounts, missingRollouts, missingWorkspacePaths);
    }

    private static void requireFile(Path path, ArrayList<String> checks, ArrayList<String> errors, String label) {
        if (Files.isRegularFile(path)) {
            checks.add("OK: " + label + " exists");
        } else {
            errors.add("Missing " + label + ": " + path);
        }
    }

    private static void requireDirectory(Path path, ArrayList<String> checks, ArrayList<String> errors, String label) {
        if (Files.isDirectory(path)) {
            checks.add("OK: " + label + " exists");
        } else {
            errors.add("Missing " + label + ": " + path);
        }
    }

    private static void detectFile(Path path, ArrayList<String> checks, ArrayList<String> warnings, String label) {
        if (Files.exists(path)) {
            checks.add("Detected " + label + ": " + path);
        } else {
            warnings.add(label + " not present");
        }
    }
}
