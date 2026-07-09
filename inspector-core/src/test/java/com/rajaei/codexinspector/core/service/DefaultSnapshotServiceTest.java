package com.rajaei.codexinspector.core.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultSnapshotServiceTest {
    @Test
    void rootChecksumChangesWhenFileContentChanges() throws Exception {
        var root = Files.createTempDirectory("codex-inspector-test");
        var file = root.resolve("a.txt");
        Files.writeString(file, "one");

        var service = new DefaultSnapshotService();
        var before = service.create(root);

        Files.writeString(file, "two");
        var after = service.create(root);

        assertThat(after.rootChecksum()).isNotEqualTo(before.rootChecksum());
        assertThat(after.totalFiles()).isEqualTo(1);
        assertThat(after.totalDirectories()).isEqualTo(1);
    }
}
