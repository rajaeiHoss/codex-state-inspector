package com.rajaei.codexinspector.core.service;

import com.rajaei.codexinspector.core.util.JsonSupport;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

class RepairServiceTest {
    @Test
    void dryRunReportsMissingWorkspaceWithoutChangingState() throws Exception {
        var codexHome = Files.createTempDirectory("codex-repair-test");
        var existing = Files.createDirectory(codexHome.resolve("existing-workspace"));
        var missing = codexHome.resolve("missing-workspace");
        var stateFile = codexHome.resolve(DefaultJsonStateReader.GLOBAL_STATE_FILE);
        Files.writeString(stateFile, state(existing.toString(), missing.toString()));
        String before = Files.readString(stateFile);

        var result = new RepairService().removeMissingWorkspaceRoots(codexHome, false);

        assertThat(result.applied()).isFalse();
        assertThat(result.backupFile()).isNull();
        assertThat(result.removedWorkspaceRoots()).containsExactly(missing.toString());
        assertThat(Files.readString(stateFile)).isEqualTo(before);
    }

    @Test
    void applyRemovesMissingWorkspaceAndCreatesBackup() throws Exception {
        var codexHome = Files.createTempDirectory("codex-repair-test");
        var existing = Files.createDirectory(codexHome.resolve("existing-workspace"));
        var missing = codexHome.resolve("missing-workspace");
        var stateFile = codexHome.resolve(DefaultJsonStateReader.GLOBAL_STATE_FILE);
        Files.writeString(stateFile, state(existing.toString(), missing.toString()));

        var result = new RepairService().removeMissingWorkspaceRoots(codexHome, true);
        var repaired = JsonSupport.mapper().readTree(stateFile.toFile());

        assertThat(result.applied()).isTrue();
        assertThat(result.backupFile()).exists();
        assertThat(repaired.path("electron-saved-workspace-roots").toString()).contains(existing.toString()).doesNotContain(missing.toString());
        assertThat(repaired.path("active-workspace-roots").toString()).contains(existing.toString()).doesNotContain(missing.toString());
        assertThat(repaired.path("electron-workspace-root-labels").has(missing.toString())).isFalse();
        assertThat(repaired.at("/electron-persisted-atom-state/composer-prompt-drafts-v1").has(missing.toString())).isFalse();
    }

    private static String state(String existing, String missing) {
        return """
                {
                  "electron-saved-workspace-roots": ["%s", "%s"],
                  "active-workspace-roots": {"%s": true, "%s": true},
                  "electron-workspace-root-labels": {
                    "%s": "Existing",
                    "%s": "Missing"
                  },
                  "electron-persisted-atom-state": {
                    "composer-prompt-drafts-v1": {
                      "%s": "keep",
                      "%s": "remove"
                    }
                  }
                }
                """.formatted(existing, missing, existing, missing, existing, missing, existing, missing);
    }
}
