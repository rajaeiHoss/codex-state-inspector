package com.rajaei.codexinspector.core.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ImportantCodexPathTest {
    @Test
    void marksKnownCodexStateFilesAndDirectories() {
        assertThat(ImportantCodexPath.isImportant(Path.of("state_5.sqlite"))).isTrue();
        assertThat(ImportantCodexPath.isImportant(Path.of("sessions/2026/rollout.jsonl"))).isTrue();
        assertThat(ImportantCodexPath.isImportant(Path.of("notes/random.txt"))).isFalse();
    }
}
