package com.rajaei.codexinspector.cli;

import java.nio.file.Path;

final class PathOptions {
    private PathOptions() {
    }

    static Path expand(String value) {
        if (value.equals("~")) {
            return Path.of(System.getProperty("user.home"));
        }
        if (value.startsWith("~/")) {
            return Path.of(System.getProperty("user.home")).resolve(value.substring(2));
        }
        return Path.of(value);
    }
}
