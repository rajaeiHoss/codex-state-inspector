package com.rajaei.codexinspector.core.service;

import com.rajaei.codexinspector.core.domain.Snapshot;

import java.io.IOException;
import java.nio.file.Path;

public interface SnapshotService {
    Snapshot create(Path source) throws IOException;
}
