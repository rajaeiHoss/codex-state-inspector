package com.rajaei.codexinspector.core.service;

import com.rajaei.codexinspector.core.domain.CodexRemoteAccount;
import com.rajaei.codexinspector.core.domain.CodexThread;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public interface SqliteStateReader {
    List<String> tables(Path codexHome) throws SQLException;

    Map<String, Long> tableCounts(Path codexHome) throws SQLException;

    List<CodexThread> threads(Path codexHome, int limit) throws SQLException;

    CodexThread thread(Path codexHome, String threadId) throws SQLException;

    List<CodexRemoteAccount> remoteAccounts(Path codexHome) throws SQLException;

    Map<String, String> schemas(Path codexHome) throws SQLException;
}
