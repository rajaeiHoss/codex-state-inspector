package com.rajaei.codexinspector.core.service;

import com.rajaei.codexinspector.core.domain.CodexRemoteAccount;
import com.rajaei.codexinspector.core.domain.CodexThread;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DefaultSqliteStateReader implements SqliteStateReader {
    public static final String DB_FILE = "state_5.sqlite";

    @Override
    public List<String> tables(Path codexHome) throws SQLException {
        try (Connection connection = connect(codexHome)) {
            var tables = new ArrayList<String>();
            try (var statement = connection.createStatement();
                 var rs = statement.executeQuery("select name from sqlite_master where type='table' order by name")) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
            return tables;
        }
    }

    @Override
    public Map<String, Long> tableCounts(Path codexHome) throws SQLException {
        try (Connection connection = connect(codexHome)) {
            var counts = new LinkedHashMap<String, Long>();
            for (String table : tables(codexHome)) {
                try (var statement = connection.createStatement();
                     var rs = statement.executeQuery("select count(*) from " + quote(table))) {
                    counts.put(table, rs.next() ? rs.getLong(1) : 0L);
                }
            }
            return counts;
        }
    }

    @Override
    public List<CodexThread> threads(Path codexHome, int limit) throws SQLException {
        try (Connection connection = connect(codexHome)) {
            if (!tableExists(connection, "threads")) {
                return List.of();
            }
            String orderColumn = firstExistingColumn(connection, "threads", "recency_at_ms", "updated_at_ms", "created_at_ms", "id");
            String sql = "select * from threads order by " + quote(orderColumn) + " desc limit ?";
            try (var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, Math.max(1, limit));
                try (var rs = statement.executeQuery()) {
                    var rows = new ArrayList<CodexThread>();
                    while (rs.next()) {
                        rows.add(toThread(rs));
                    }
                    return rows;
                }
            }
        }
    }

    @Override
    public CodexThread thread(Path codexHome, String threadId) throws SQLException {
        try (Connection connection = connect(codexHome)) {
            if (!tableExists(connection, "threads")) {
                return null;
            }
            String idColumn = firstExistingColumn(connection, "threads", "id", "thread_id");
            try (var statement = connection.prepareStatement("select * from threads where " + quote(idColumn) + " = ?")) {
                statement.setString(1, threadId);
                try (var rs = statement.executeQuery()) {
                    return rs.next() ? toThread(rs) : null;
                }
            }
        }
    }

    @Override
    public List<CodexRemoteAccount> remoteAccounts(Path codexHome) throws SQLException {
        try (Connection connection = connect(codexHome)) {
            if (!tableExists(connection, "remote_control_enrollments")) {
                return List.of();
            }
            try (var statement = connection.createStatement();
                 var rs = statement.executeQuery("select * from remote_control_enrollments")) {
                var accounts = new ArrayList<CodexRemoteAccount>();
                while (rs.next()) {
                    accounts.add(new CodexRemoteAccount(
                            text(rs, "id", "account_id", "email"),
                            text(rs, "provider", "auth_provider", "websocket_url"),
                            text(rs, "label", "email", "name", "server_name", "environment_id")
                    ));
                }
                return accounts;
            }
        }
    }

    @Override
    public Map<String, String> schemas(Path codexHome) throws SQLException {
        try (Connection connection = connect(codexHome);
             var statement = connection.createStatement();
             var rs = statement.executeQuery("select name, sql from sqlite_master where type='table' order by name")) {
            var schemas = new LinkedHashMap<String, String>();
            while (rs.next()) {
                schemas.put(rs.getString("name"), rs.getString("sql"));
            }
            return schemas;
        }
    }

    private static Connection connect(Path codexHome) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + codexHome.resolve(DB_FILE).toAbsolutePath());
    }

    private static CodexThread toThread(ResultSet rs) throws SQLException {
        return new CodexThread(
                text(rs, "id", "thread_id"),
                text(rs, "title", "name"),
                text(rs, "cwd", "workspace_path"),
                text(rs, "rollout_path"),
                gitInfo(rs),
                text(rs, "model"),
                text(rs, "reasoning_effort"),
                number(rs, "tokens_used", "total_tokens"),
                bool(rs, "archived", "is_archived"),
                number(rs, "created_at_ms", "created_at"),
                number(rs, "updated_at_ms", "updated_at"),
                number(rs, "recency_at_ms", "recency_at")
        );
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (var statement = connection.prepareStatement("select 1 from sqlite_master where type='table' and name = ?")) {
            statement.setString(1, table);
            try (var rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String firstExistingColumn(Connection connection, String table, String... candidates) throws SQLException {
        var columns = columns(connection, table);
        for (String candidate : candidates) {
            if (columns.contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
        return candidates[candidates.length - 1];
    }

    private static List<String> columns(Connection connection, String table) throws SQLException {
        var columns = new ArrayList<String>();
        try (var statement = connection.createStatement();
             var rs = statement.executeQuery("pragma table_info(" + quote(table) + ")")) {
            while (rs.next()) {
                columns.add(rs.getString("name").toLowerCase(Locale.ROOT));
            }
        }
        return columns;
    }

    private static String text(ResultSet rs, String... columns) throws SQLException {
        for (String column : columns) {
            if (hasColumn(rs, column)) {
                String value = rs.getString(column);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private static Long number(ResultSet rs, String... columns) throws SQLException {
        for (String column : columns) {
            if (hasColumn(rs, column)) {
                long value = rs.getLong(column);
                if (!rs.wasNull()) {
                    return value;
                }
            }
        }
        return null;
    }

    private static boolean bool(ResultSet rs, String... columns) throws SQLException {
        Long value = number(rs, columns);
        if (value != null) {
            return value != 0L;
        }
        String text = text(rs, columns);
        return Boolean.parseBoolean(text);
    }

    private static String gitInfo(ResultSet rs) throws SQLException {
        String direct = text(rs, "git_info", "git");
        if (direct != null) {
            return direct;
        }
        String sha = text(rs, "git_sha");
        String branch = text(rs, "git_branch");
        String origin = text(rs, "git_origin_url");
        if (sha == null && branch == null && origin == null) {
            return null;
        }
        return "branch=%s sha=%s origin=%s".formatted(value(branch), value(sha), value(origin));
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static boolean hasColumn(ResultSet rs, String column) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            if (metaData.getColumnName(i).equalsIgnoreCase(column)) {
                return true;
            }
        }
        return false;
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
