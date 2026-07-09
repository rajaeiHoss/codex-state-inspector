package com.rajaei.codexinspector.core.service;

import java.io.IOException;
import java.nio.file.Path;

public interface ChecksumService {
    String sha256(Path path) throws IOException;

    String sha256(String value);
}
