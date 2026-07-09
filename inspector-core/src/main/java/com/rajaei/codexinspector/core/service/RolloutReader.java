package com.rajaei.codexinspector.core.service;

import com.rajaei.codexinspector.core.domain.RolloutSummary;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;

public interface RolloutReader {
    RolloutSummary summarize(Path codexHome, String threadId) throws IOException, SQLException;
}
