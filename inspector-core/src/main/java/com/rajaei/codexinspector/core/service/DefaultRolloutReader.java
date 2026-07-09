package com.rajaei.codexinspector.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.rajaei.codexinspector.core.domain.RolloutSummary;
import com.rajaei.codexinspector.core.util.JsonSupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.regex.Pattern;

public final class DefaultRolloutReader implements RolloutReader {
    private static final Pattern FILE_PATTERN = Pattern.compile("((?:/|\\./|\\.\\./)[^\\\\\\s\"'`>,)]*\\.(?:java|xml|json|jsonl|md|txt|yml|yaml|properties|sql|sqlite|py|ts|tsx|js|jsx|css|html|sh|toml|csv|pdf|docx|xlsx)(?::\\d+)?|\\b[A-Za-z0-9_-]+\\.(?:java|xml|json|jsonl|md|txt|yml|yaml|properties|sql|sqlite|py|ts|tsx|js|jsx|css|html|sh|toml|csv|pdf|docx|xlsx)\\b)");
    private final SqliteStateReader sqliteReader = new DefaultSqliteStateReader();

    @Override
    public RolloutSummary summarize(Path codexHome, String threadId) throws IOException, SQLException {
        var thread = sqliteReader.thread(codexHome, threadId);
        if (thread == null || thread.rolloutPath() == null) {
            throw new IOException("No rollout_path found for thread: " + threadId);
        }
        Path rollout = Path.of(thread.rolloutPath());
        if (!rollout.isAbsolute()) {
            rollout = codexHome.resolve(rollout).normalize();
        }
        var files = new LinkedHashSet<String>();
        long records = 0;
        long userMessages = 0;
        long assistantMessages = 0;
        long toolCalls = 0;
        long commands = 0;
        String firstUser = null;
        String lastUser = null;

        for (String line : Files.readAllLines(rollout)) {
            if (line.isBlank()) {
                continue;
            }
            records++;
            JsonNode node = JsonSupport.mapper().readTree(line);
            String text = node.toString();
            String role = findText(node, "role");
            if ("user".equals(role)) {
                userMessages++;
                String message = extractMessage(node);
                if (firstUser == null) {
                    firstUser = message;
                }
                lastUser = message;
            } else if ("assistant".equals(role)) {
                assistantMessages++;
            }
            if (text.contains("tool_call") || text.contains("\"tool_calls\"")) {
                toolCalls++;
            }
            if (text.contains("exec_command") || text.contains("\"cmd\"")) {
                commands++;
            }
            collectReferencedFiles(text, files);
        }
        return new RolloutSummary(threadId, rollout.toString(), records, userMessages, assistantMessages, toolCalls, commands, files, firstUser, lastUser);
    }

    private static String findText(JsonNode node, String field) {
        if (node.has(field) && node.get(field).isTextual()) {
            return node.get(field).asText();
        }
        for (JsonNode child : node) {
            String value = findText(child, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String extractMessage(JsonNode node) {
        String content = findText(node, "content");
        if (content == null) {
            content = findText(node, "text");
        }
        if (content == null) {
            content = node.toString();
        }
        return content.length() > 240 ? content.substring(0, 240) + "..." : content;
    }

    private static void collectReferencedFiles(String text, LinkedHashSet<String> files) {
        var matcher = FILE_PATTERN.matcher(text);
        while (matcher.find() && files.size() < 50) {
            String candidate = matcher.group(1)
                    .replace("\\", "")
                    .replaceAll("[.,;:]+$", "");
            if (isUsefulFileReference(candidate)) {
                files.add(candidate);
            }
        }
    }

    private static boolean isUsefulFileReference(String candidate) {
        if (candidate.isBlank() || candidate.startsWith("//") || candidate.startsWith("http://") || candidate.startsWith("https://")) {
            return false;
        }
        if (candidate.matches("\\d+\\.\\d+\\.\\d+.*") || candidate.endsWith(".Z")) {
            return false;
        }
        if (candidate.equals("Node.js") || candidate.equals("Three.js") || candidate.equals("/Three.js")) {
            return false;
        }
        return candidate.contains("/") || candidate.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9]{1,8}");
    }
}
