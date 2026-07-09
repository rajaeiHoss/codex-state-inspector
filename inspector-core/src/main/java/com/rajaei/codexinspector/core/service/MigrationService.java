package com.rajaei.codexinspector.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.rajaei.codexinspector.core.domain.MigrationChange;
import com.rajaei.codexinspector.core.domain.MigrationPlan;
import com.rajaei.codexinspector.core.domain.MigrationResult;
import com.rajaei.codexinspector.core.util.JsonSupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class MigrationService {
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC);
    private static final Map<String, List<String>> SQLITE_PATH_COLUMNS = Map.of(
            "threads", List.of("cwd", "rollout_path", "agent_path"),
            "agent_jobs", List.of("input_csv_path", "output_csv_path")
    );

    public MigrationPlan plan(Path codexHome, String fromPrefix, String toPrefix) throws IOException, SQLException {
        validatePrefixes(fromPrefix, toPrefix);
        Path normalizedHome = codexHome.toAbsolutePath().normalize();
        var globalChanges = planGlobalState(normalizedHome, fromPrefix, toPrefix);
        var sqliteChanges = planSqlite(normalizedHome, fromPrefix, toPrefix);
        return new MigrationPlan(normalizedHome, fromPrefix, toPrefix, globalChanges, sqliteChanges);
    }

    public MigrationResult apply(Path codexHome, String fromPrefix, String toPrefix) throws IOException, SQLException {
        MigrationPlan plan = plan(codexHome, fromPrefix, toPrefix);
        var backups = new ArrayList<Path>();
        if (!plan.hasChanges()) {
            return new MigrationResult(plan, true, backups);
        }

        Path globalState = plan.codexHome().resolve(DefaultJsonStateReader.GLOBAL_STATE_FILE);
        Path sqlite = plan.codexHome().resolve(DefaultSqliteStateReader.DB_FILE);
        backups.add(copyBackup(globalState));
        if (Files.isRegularFile(sqlite) && !plan.sqliteChanges().isEmpty()) {
            backups.add(copyBackup(sqlite));
            copyIfExists(sqlite.resolveSibling(sqlite.getFileName() + "-wal"), backups);
            copyIfExists(sqlite.resolveSibling(sqlite.getFileName() + "-shm"), backups);
        }

        ObjectNode root = (ObjectNode) JsonSupport.mapper().readTree(globalState.toFile());
        JsonNode migrated = migrateJson(root, "$", fromPrefix, toPrefix, null);
        JsonSupport.mapper().writeValue(globalState.toFile(), migrated);
        applySqlite(plan.codexHome(), fromPrefix, toPrefix);
        return new MigrationResult(plan, true, backups);
    }

    private static List<MigrationChange> planGlobalState(Path codexHome, String fromPrefix, String toPrefix) throws IOException {
        Path globalState = codexHome.resolve(DefaultJsonStateReader.GLOBAL_STATE_FILE);
        if (!Files.isRegularFile(globalState)) {
            return List.of();
        }
        var changes = new ArrayList<MigrationChange>();
        JsonNode root = JsonSupport.mapper().readTree(globalState.toFile());
        migrateJson(root, "$", fromPrefix, toPrefix, changes);
        return changes;
    }

    private static JsonNode migrateJson(JsonNode node, String location, String fromPrefix, String toPrefix, List<MigrationChange> changes) {
        if (node.isTextual()) {
            String before = node.asText();
            String after = rewritePrefix(before, fromPrefix, toPrefix);
            if (!before.equals(after)) {
                if (changes != null) {
                    changes.add(new MigrationChange(location, before, after));
                }
                return TextNode.valueOf(after);
            }
            return node;
        }
        if (node.isArray()) {
            ArrayNode array = JsonSupport.mapper().createArrayNode();
            for (int i = 0; i < node.size(); i++) {
                array.add(migrateJson(node.get(i), location + "[" + i + "]", fromPrefix, toPrefix, changes));
            }
            return array;
        }
        if (node.isObject()) {
            ObjectNode object = JsonSupport.mapper().createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String beforeKey = field.getKey();
                String afterKey = rewritePrefix(beforeKey, fromPrefix, toPrefix);
                if (!beforeKey.equals(afterKey) && changes != null) {
                    changes.add(new MigrationChange(location + "." + beforeKey + " <key>", beforeKey, afterKey));
                }
                object.set(afterKey, migrateJson(field.getValue(), location + "." + afterKey, fromPrefix, toPrefix, changes));
            }
            return object;
        }
        return node;
    }

    private static List<MigrationChange> planSqlite(Path codexHome, String fromPrefix, String toPrefix) throws SQLException {
        Path sqlite = codexHome.resolve(DefaultSqliteStateReader.DB_FILE);
        if (!Files.isRegularFile(sqlite)) {
            return List.of();
        }
        var changes = new ArrayList<MigrationChange>();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + sqlite.toAbsolutePath())) {
            for (Map.Entry<String, List<String>> table : SQLITE_PATH_COLUMNS.entrySet()) {
                if (!tableExists(connection, table.getKey())) {
                    continue;
                }
                for (String column : table.getValue()) {
                    if (!columnExists(connection, table.getKey(), column)) {
                        continue;
                    }
                    String idColumn = columnExists(connection, table.getKey(), "id") ? "id" : "rowid";
                    String sql = "select " + quote(idColumn) + " as row_id, " + quote(column) + " as value from " + quote(table.getKey()) + " where " + quote(column) + " like ?";
                    try (var statement = connection.prepareStatement(sql)) {
                        statement.setString(1, fromPrefix + "%");
                        try (var rs = statement.executeQuery()) {
                            while (rs.next()) {
                                String before = rs.getString("value");
                                changes.add(new MigrationChange(table.getKey() + "." + column + "[" + rs.getString("row_id") + "]", before, rewritePrefix(before, fromPrefix, toPrefix)));
                            }
                        }
                    }
                }
            }
        }
        return changes;
    }

    private static void applySqlite(Path codexHome, String fromPrefix, String toPrefix) throws SQLException {
        Path sqlite = codexHome.resolve(DefaultSqliteStateReader.DB_FILE);
        if (!Files.isRegularFile(sqlite)) {
            return;
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + sqlite.toAbsolutePath())) {
            for (Map.Entry<String, List<String>> table : SQLITE_PATH_COLUMNS.entrySet()) {
                if (!tableExists(connection, table.getKey())) {
                    continue;
                }
                for (String column : table.getValue()) {
                    if (!columnExists(connection, table.getKey(), column)) {
                        continue;
                    }
                    String sql = "update " + quote(table.getKey()) + " set " + quote(column) + " = ? || substr(" + quote(column) + ", ?) where " + quote(column) + " like ?";
                    try (var statement = connection.prepareStatement(sql)) {
                        statement.setString(1, toPrefix);
                        statement.setInt(2, fromPrefix.length() + 1);
                        statement.setString(3, fromPrefix + "%");
                        statement.executeUpdate();
                    }
                }
            }
        }
    }

    private static boolean tableExists(java.sql.Connection connection, String table) throws SQLException {
        try (var statement = connection.prepareStatement("select 1 from sqlite_master where type='table' and name = ?")) {
            statement.setString(1, table);
            try (var rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean columnExists(java.sql.Connection connection, String table, String column) throws SQLException {
        try (var statement = connection.createStatement();
             var rs = statement.executeQuery("pragma table_info(" + quote(table) + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void validatePrefixes(String fromPrefix, String toPrefix) {
        if (fromPrefix == null || fromPrefix.isBlank() || toPrefix == null || toPrefix.isBlank()) {
            throw new IllegalArgumentException("Both prefixes are required.");
        }
        if (fromPrefix.equals(toPrefix)) {
            throw new IllegalArgumentException("Prefixes must be different.");
        }
        if (!fromPrefix.startsWith("/") || !toPrefix.startsWith("/")) {
            throw new IllegalArgumentException("Only absolute path prefixes are supported.");
        }
    }

    private static String rewritePrefix(String value, String fromPrefix, String toPrefix) {
        if (value != null && value.startsWith(fromPrefix)) {
            return toPrefix + value.substring(fromPrefix.length());
        }
        return value;
    }

    private static Path copyBackup(Path file) throws IOException {
        Path backup = backupPath(file);
        Files.copy(file, backup, StandardCopyOption.COPY_ATTRIBUTES);
        return backup;
    }

    private static void copyIfExists(Path file, List<Path> backups) throws IOException {
        if (Files.exists(file)) {
            backups.add(copyBackup(file));
        }
    }

    private static Path backupPath(Path file) {
        String timestamp = BACKUP_TIMESTAMP.format(Instant.now());
        return file.resolveSibling(file.getFileName() + ".bak-" + timestamp);
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
