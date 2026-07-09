package com.rajaei.codexinspector.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.rajaei.codexinspector.core.domain.CodexWorkspace;
import com.rajaei.codexinspector.core.util.JsonSupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DefaultJsonStateReader implements JsonStateReader {
    public static final String GLOBAL_STATE_FILE = ".codex-global-state.json";

    @Override
    public JsonNode readGlobalState(Path codexHome) throws IOException {
        return JsonSupport.mapper().readTree(codexHome.resolve(GLOBAL_STATE_FILE).toFile());
    }

    @Override
    public List<CodexWorkspace> readWorkspaces(Path codexHome) throws IOException {
        JsonNode root = readGlobalState(codexHome);
        Set<String> saved = textSet(root.path("electron-saved-workspace-roots"));
        Set<String> active = textSet(root.path("active-workspace-roots"));
        JsonNode labels = root.path("electron-workspace-root-labels");

        Set<String> all = new HashSet<>(saved);
        all.addAll(active);
        var workspaces = new ArrayList<CodexWorkspace>();
        all.stream().sorted().forEach(path -> workspaces.add(new CodexWorkspace(
                path,
                labels.path(path).asText(null),
                active.contains(path),
                Files.exists(Path.of(path))
        )));
        return workspaces;
    }

    public static Set<String> textSet(JsonNode node) {
        Set<String> values = new HashSet<>();
        if (node == null || node.isMissingNode() || node.isNull()) {
            return values;
        }
        if (node.isArray()) {
            node.forEach(item -> {
                if (item.isTextual()) {
                    values.add(item.asText());
                }
            });
        } else if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (field.getValue().asBoolean(false)) {
                    values.add(field.getKey());
                }
            }
        }
        return values;
    }
}
