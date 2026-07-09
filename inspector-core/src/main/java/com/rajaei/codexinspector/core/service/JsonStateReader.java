package com.rajaei.codexinspector.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.rajaei.codexinspector.core.domain.CodexWorkspace;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface JsonStateReader {
    JsonNode readGlobalState(Path codexHome) throws IOException;

    List<CodexWorkspace> readWorkspaces(Path codexHome) throws IOException;
}
