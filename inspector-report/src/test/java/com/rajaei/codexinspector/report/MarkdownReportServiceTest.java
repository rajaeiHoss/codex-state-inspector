package com.rajaei.codexinspector.report;

import com.rajaei.codexinspector.core.domain.CodexRemoteAccount;
import com.rajaei.codexinspector.core.domain.CodexState;
import com.rajaei.codexinspector.core.domain.CodexThread;
import com.rajaei.codexinspector.core.domain.CodexWorkspace;
import com.rajaei.codexinspector.core.domain.DoctorReport;
import com.rajaei.codexinspector.core.service.CodexStateService;
import com.rajaei.codexinspector.core.service.DoctorService;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownReportServiceTest {
    @Test
    void generatesMarkdownAuditReport() throws Exception {
        var doctor = new StubDoctorService();
        var state = new StubCodexStateService();
        var report = new MarkdownReportService(doctor, state).generate(Path.of("/tmp/codex"));

        assertThat(report).contains("# Codex State Report");
        assertThat(report).contains("## Doctor");
        assertThat(report).contains("## State Summary");
        assertThat(report).contains("## Recent Threads");
        assertThat(report).contains("## SQLite Tables");
        assertThat(report).contains("thread_dynamic_tools");
    }

    private static final class StubDoctorService extends DoctorService {
        @Override
        public DoctorReport check(Path source) {
            return new DoctorReport(true, List.of("OK"), List.of(), List.of(), 1, 1, 1, 0, 1, 0, 0);
        }
    }

    private static final class StubCodexStateService extends CodexStateService {
        @Override
        public CodexState inspect(Path codexHome) {
            return new CodexState(
                    List.of(new CodexWorkspace("/Users/example/project", "Project", true, true)),
                    List.of(new CodexThread("thread-1", "Title", "/Users/example/project", "/tmp/r.jsonl", null, "gpt", "medium", 12L, false, 1L, 2L, 3L)),
                    List.of(new CodexRemoteAccount("account-1", "provider", "label")),
                    Map.of("threads", 1L, "thread_dynamic_tools", 2L),
                    Set.of("count=2"),
                    0,
                    0
            );
        }
    }
}
