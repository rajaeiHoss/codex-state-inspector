package com.rajaei.codexinspector.core.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultSqliteStateReaderTest {
    @Test
    void readsCurrentThreadSchemaFields() throws Exception {
        var codexHome = Files.createTempDirectory("codex-sqlite-test");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + codexHome.resolve("state_5.sqlite"))) {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("""
                        create table threads (
                            id text primary key,
                            rollout_path text not null,
                            created_at integer not null,
                            updated_at integer not null,
                            source text not null,
                            model_provider text not null,
                            cwd text not null,
                            title text not null,
                            sandbox_policy text not null,
                            approval_mode text not null,
                            tokens_used integer not null default 0,
                            archived integer not null default 0,
                            git_sha text,
                            git_branch text,
                            git_origin_url text,
                            model text,
                            reasoning_effort text,
                            created_at_ms integer,
                            updated_at_ms integer,
                            recency_at_ms integer not null default 0
                        )
                        """);
                statement.executeUpdate("""
                        insert into threads (
                            id, rollout_path, created_at, updated_at, source, model_provider, cwd, title,
                            sandbox_policy, approval_mode, tokens_used, archived, git_sha, git_branch,
                            git_origin_url, model, reasoning_effort, created_at_ms, updated_at_ms, recency_at_ms
                        ) values (
                            'thread-1', '/tmp/rollout.jsonl', 1, 2, 'desktop', 'openai', '/tmp/work',
                            'Fixture Thread', 'workspace-write', 'never', 42, 1, 'abc123', 'main',
                            'git@example.test/repo.git', 'gpt-test', 'medium', 1000, 2000, 3000
                        )
                        """);
            }
        }

        var thread = new DefaultSqliteStateReader().thread(codexHome, "thread-1");

        assertThat(thread.title()).isEqualTo("Fixture Thread");
        assertThat(thread.archived()).isTrue();
        assertThat(thread.tokensUsed()).isEqualTo(42);
        assertThat(thread.gitInfo()).contains("branch=main", "sha=abc123");
        assertThat(thread.recencyAtMs()).isEqualTo(3000);
    }
}
