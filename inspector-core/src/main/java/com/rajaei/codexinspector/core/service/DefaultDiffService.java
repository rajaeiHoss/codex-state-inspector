package com.rajaei.codexinspector.core.service;

import com.rajaei.codexinspector.core.domain.DiffResult;
import com.rajaei.codexinspector.core.domain.Snapshot;
import com.rajaei.codexinspector.core.domain.SnapshotDirectory;
import com.rajaei.codexinspector.core.domain.SnapshotFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class DefaultDiffService implements DiffService {
    @Override
    public DiffResult diff(Snapshot before, Snapshot after) {
        Map<String, SnapshotFile> beforeFiles = new HashMap<>();
        Map<String, SnapshotFile> afterFiles = new HashMap<>();
        Map<String, SnapshotDirectory> beforeDirectories = new HashMap<>();
        Map<String, SnapshotDirectory> afterDirectories = new HashMap<>();
        flatten(before.tree(), beforeFiles, beforeDirectories);
        flatten(after.tree(), afterFiles, afterDirectories);

        var added = new ArrayList<String>();
        var removed = new ArrayList<String>();
        var modified = new ArrayList<String>();
        var changedDirectories = new ArrayList<String>();

        afterFiles.forEach((path, file) -> {
            SnapshotFile previous = beforeFiles.get(path);
            if (previous == null) {
                added.add(path);
            } else if (!previous.sha256().equals(file.sha256()) || !previous.type().equals(file.type())) {
                modified.add(path);
            }
        });
        beforeFiles.keySet().stream().filter(path -> !afterFiles.containsKey(path)).forEach(removed::add);
        afterDirectories.forEach((path, directory) -> {
            SnapshotDirectory previous = beforeDirectories.get(path);
            if (previous != null && !previous.sha256().equals(directory.sha256())) {
                changedDirectories.add(path);
            }
        });

        added.sort(String::compareTo);
        removed.sort(String::compareTo);
        modified.sort(String::compareTo);
        changedDirectories.sort(String::compareTo);
        return new DiffResult(added, removed, modified, changedDirectories);
    }

    private static void flatten(SnapshotDirectory directory, Map<String, SnapshotFile> files, Map<String, SnapshotDirectory> directories) {
        directories.put(directory.path(), directory);
        directory.files().forEach(file -> files.put(file.path(), file));
        directory.directories().forEach(child -> flatten(child, files, directories));
    }
}
