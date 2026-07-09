package com.rajaei.codexinspector.core.service;

import com.rajaei.codexinspector.core.util.JsonSupport;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationServiceTest {
    @Test
    void plansAndAppliesJsonAndSqlitePathMigration() throws Exception {
        var codexHome = Files.createTempDirectory("codex-migrate-test");
        Files.writeString(codexHome.resolve(DefaultJsonStateReader.GLOBAL_STATE_FILE), """
                {
                  "electron-saved-workspace-roots": ["/Users/old/project"],
                  "active-workspace-roots": {"/Users/old/project": true},
                  "electron-workspace-root-labels": {
                    "/Users/old/project": "Project"
                  },
                  "other": "/Users/old/project/file.txt"
                }
                """);
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + codexHome.resolve(DefaultSqliteStateReader.DB_FILE))) {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("""
                        create table threads (
                            id text primary key,
                            cwd text not null,
                            rollout_path text not null,
                            agent_path text
                        )
                        """);
                statement.executeUpdate("""
                        insert into threads (id, cwd, rollout_path, agent_path)
                        values ('thread-1', '/Users/old/project', '/Users/old/.codex/sessions/r.jsonl', null)
                        """);
            }
        }

        var service = new MigrationService();
        var plan = service.plan(codexHome, "/Users/old", "/Users/new");

        assertThat(plan.globalStateChanges()).hasSize(4);
        assertThat(plan.sqliteChanges()).hasSize(2);

        var result = service.apply(codexHome, "/Users/old", "/Users/new");
        var state = JsonSupport.mapper().readTree(codexHome.resolve(DefaultJsonStateReader.GLOBAL_STATE_FILE).toFile());

        assertThat(result.backupFiles()).hasSize(2);
        assertThat(state.path("electron-saved-workspace-roots").get(0).asText()).isEqualTo("/Users/new/project");
        assertThat(state.path("active-workspace-roots").has("/Users/new/project")).isTrue();
        assertThat(state.path("other").asText()).isEqualTo("/Users/new/project/file.txt");

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + codexHome.resolve(DefaultSqliteStateReader.DB_FILE));
             var statement = connection.createStatement();
             var rs = statement.executeQuery("select cwd, rollout_path from threads where id = 'thread-1'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("cwd")).isEqualTo("/Users/new/project");
            assertThat(rs.getString("rollout_path")).isEqualTo("/Users/new/.codex/sessions/r.jsonl");
        }
    }
}
