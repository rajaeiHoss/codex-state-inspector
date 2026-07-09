package com.rajaei.codexinspector.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rajaei.codexinspector.core.domain.RepairResult;
import com.rajaei.codexinspector.core.util.JsonSupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class RepairService {
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC);

    public RepairResult removeMissingWorkspaceRoots(Path codexHome, boolean apply) throws IOException {
        Path normalizedHome = codexHome.toAbsolutePath().normalize();
        Path stateFile = normalizedHome.resolve(DefaultJsonStateReader.GLOBAL_STATE_FILE);
        ObjectNode root = (ObjectNode) JsonSupport.mapper().readTree(stateFile.toFile());
        List<String> missingRoots = missingWorkspaceRoots(root);
        Path backupFile = null;

        if (apply && !missingRoots.isEmpty()) {
            backupFile = backupPath(stateFile);
            Files.copy(stateFile, backupFile, StandardCopyOption.COPY_ATTRIBUTES);
            removeRoots(root, "electron-saved-workspace-roots", missingRoots);
            removeRoots(root, "active-workspace-roots", missingRoots);
            removeObjectFields(root.path("electron-workspace-root-labels"), missingRoots);
            removeObjectFields(root.at("/electron-persisted-atom-state/composer-prompt-drafts-v1"), missingRoots);
            JsonSupport.mapper().writeValue(stateFile.toFile(), root);
        }

        return new RepairResult(apply, stateFile, backupFile, missingRoots);
    }

    private static List<String> missingWorkspaceRoots(ObjectNode root) {
        var roots = new java.util.TreeSet<String>();
        roots.addAll(DefaultJsonStateReader.textSet(root.path("electron-saved-workspace-roots")));
        roots.addAll(DefaultJsonStateReader.textSet(root.path("active-workspace-roots")));
        return roots.stream()
                .filter(path -> !Files.exists(Path.of(path)))
                .toList();
    }

    private static void removeRoots(ObjectNode root, String fieldName, List<String> missingRoots) {
        JsonNode node = root.path(fieldName);
        if (node.isArray()) {
            ArrayNode replacement = JsonSupport.mapper().createArrayNode();
            node.forEach(item -> {
                if (!item.isTextual() || !missingRoots.contains(item.asText())) {
                    replacement.add(item);
                }
            });
            root.set(fieldName, replacement);
        } else if (node.isObject()) {
            removeObjectFields(node, missingRoots);
        }
    }

    private static void removeObjectFields(JsonNode node, List<String> fields) {
        if (!node.isObject()) {
            return;
        }
        ObjectNode object = (ObjectNode) node;
        for (String field : fields) {
            object.remove(field);
        }
    }

    private static Path backupPath(Path stateFile) {
        String timestamp = BACKUP_TIMESTAMP.format(Instant.now());
        return stateFile.resolveSibling(stateFile.getFileName() + ".bak-" + timestamp);
    }
}
