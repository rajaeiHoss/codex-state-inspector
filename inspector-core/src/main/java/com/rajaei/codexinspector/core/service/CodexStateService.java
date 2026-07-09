package com.rajaei.codexinspector.core.service;

import com.rajaei.codexinspector.core.domain.CodexState;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;

public class CodexStateService {
    private final JsonStateReader jsonReader;
    private final SqliteStateReader sqliteReader;

    public CodexStateService() {
        this(new DefaultJsonStateReader(), new DefaultSqliteStateReader());
    }

    public CodexStateService(JsonStateReader jsonReader, SqliteStateReader sqliteReader) {
        this.jsonReader = jsonReader;
        this.sqliteReader = sqliteReader;
    }

    public CodexState inspect(Path codexHome) throws IOException, SQLException {
        var workspaces = jsonReader.readWorkspaces(codexHome);
        var threads = sqliteReader.threads(codexHome, Integer.MAX_VALUE);
        var accounts = sqliteReader.remoteAccounts(codexHome);
        var counts = sqliteReader.tableCounts(codexHome);
        long jobs = counts.getOrDefault("agent_jobs", 0L);
        long edges = counts.getOrDefault("thread_spawn_edges", 0L);
        long dynamicToolThreads = counts.getOrDefault("thread_dynamic_tools", 0L);
        return new CodexState(workspaces, threads, accounts, counts, dynamicToolThreads == 0 ? java.util.Set.of() : java.util.Set.of("count=" + dynamicToolThreads), jobs, edges);
    }
}
