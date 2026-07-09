package com.rajaei.codexinspector.report;

import com.rajaei.codexinspector.core.domain.CodexState;
import com.rajaei.codexinspector.core.domain.CodexThread;
import com.rajaei.codexinspector.core.domain.DoctorReport;
import com.rajaei.codexinspector.core.service.CodexStateService;
import com.rajaei.codexinspector.core.service.DoctorService;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

public final class MarkdownReportService {
    private final DoctorService doctorService;
    private final CodexStateService stateService;

    public MarkdownReportService() {
        this(new DoctorService(), new CodexStateService());
    }

    public MarkdownReportService(DoctorService doctorService, CodexStateService stateService) {
        this.doctorService = doctorService;
        this.stateService = stateService;
    }

    public String generate(Path source) throws IOException, SQLException {
        Path codexHome = source.toAbsolutePath().normalize();
        DoctorReport doctor = doctorService.check(codexHome);
        CodexState state = stateService.inspect(codexHome);
        StringBuilder report = new StringBuilder();
        report.append("# Codex State Report\n\n");
        report.append("- Generated at: `").append(Instant.now()).append("`\n");
        report.append("- Source: `").append(codexHome).append("`\n");
        report.append("- Health: **").append(healthLabel(doctor)).append("**\n\n");
        appendDoctor(report, doctor);
        appendSummary(report, state);
        appendWorkspaces(report, state);
        appendThreads(report, state);
        appendTables(report, state.tableCounts());
        appendRecommendations(report, doctor, state);
        return report.toString();
    }

    private static void appendDoctor(StringBuilder report, DoctorReport doctor) {
        report.append("## Doctor\n\n");
        report.append("| Metric | Value |\n| --- | ---: |\n");
        report.append("| Workspaces | ").append(doctor.workspaces()).append(" |\n");
        report.append("| Active workspaces | ").append(doctor.activeWorkspaces()).append(" |\n");
        report.append("| Threads | ").append(doctor.threads()).append(" |\n");
        report.append("| Archived threads | ").append(doctor.archivedThreads()).append(" |\n");
        report.append("| Remote accounts | ").append(doctor.remoteAccounts()).append(" |\n");
        report.append("| Missing rollout files | ").append(doctor.missingRolloutFiles()).append(" |\n");
        report.append("| Missing workspace paths | ").append(doctor.missingWorkspacePaths()).append(" |\n\n");
        if (!doctor.errors().isEmpty()) {
            report.append("### Errors\n\n");
            doctor.errors().forEach(error -> report.append("- ").append(escape(error)).append('\n'));
            report.append('\n');
        }
        if (!doctor.warnings().isEmpty()) {
            report.append("### Warnings\n\n");
            doctor.warnings().forEach(warning -> report.append("- ").append(escape(warning)).append('\n'));
            report.append('\n');
        }
    }

    private static void appendSummary(StringBuilder report, CodexState state) {
        report.append("## State Summary\n\n");
        report.append("| Metric | Value |\n| --- | ---: |\n");
        report.append("| Workspaces | ").append(state.workspaces().size()).append(" |\n");
        report.append("| Active workspaces | ").append(state.activeWorkspaceCount()).append(" |\n");
        report.append("| Threads | ").append(state.threads().size()).append(" |\n");
        report.append("| Archived threads | ").append(state.archivedThreadCount()).append(" |\n");
        report.append("| Remote accounts | ").append(state.remoteAccounts().size()).append(" |\n");
        report.append("| Dynamic tools | ").append(state.tableCounts().getOrDefault("thread_dynamic_tools", 0L)).append(" |\n");
        report.append("| Agent jobs | ").append(state.agentJobs()).append(" |\n");
        report.append("| Spawn edges | ").append(state.spawnEdges()).append(" |\n\n");
    }

    private static void appendWorkspaces(StringBuilder report, CodexState state) {
        report.append("## Top Workspaces\n\n");
        report.append("| Workspace | Active | Exists |\n| --- | --- | --- |\n");
        state.workspaces().stream().limit(20).forEach(workspace ->
                report.append("| `").append(escape(workspace.path())).append("` | ")
                        .append(workspace.active() ? "yes" : "no").append(" | ")
                        .append(workspace.exists() ? "yes" : "no").append(" |\n"));
        report.append('\n');
    }

    private static void appendThreads(StringBuilder report, CodexState state) {
        report.append("## Recent Threads\n\n");
        report.append("| Thread | Title | CWD | Archived | Tokens |\n| --- | --- | --- | --- | ---: |\n");
        state.threads().stream()
                .sorted(Comparator.comparingLong(thread -> -coalesce(thread.recencyAtMs(), thread.updatedAtMs(), thread.createdAtMs(), 0L)))
                .limit(20)
                .forEach(thread -> appendThread(report, thread));
        report.append('\n');
    }

    private static void appendThread(StringBuilder report, CodexThread thread) {
        report.append("| `").append(escape(thread.id())).append("` | ")
                .append(escape(truncate(thread.title(), 80))).append(" | `")
                .append(escape(truncate(thread.cwd(), 80))).append("` | ")
                .append(thread.archived() ? "yes" : "no").append(" | ")
                .append(thread.tokensUsed() == null ? 0 : thread.tokensUsed()).append(" |\n");
    }

    private static void appendTables(StringBuilder report, Map<String, Long> tableCounts) {
        report.append("## SQLite Tables\n\n");
        report.append("| Table | Rows |\n| --- | ---: |\n");
        tableCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> report.append("| `").append(escape(entry.getKey())).append("` | ").append(entry.getValue()).append(" |\n"));
        report.append('\n');
    }

    private static void appendRecommendations(StringBuilder report, DoctorReport doctor, CodexState state) {
        report.append("## Recommendations\n\n");
        if (doctor.healthy() && doctor.warnings().isEmpty()) {
            report.append("- No immediate repair action is required.\n");
        }
        if (doctor.missingWorkspacePaths() > 0) {
            report.append("- Run `repair --source <codex-home> missing-workspaces` and review the dry-run output.\n");
        }
        if (doctor.missingRolloutFiles() > 0) {
            report.append("- Investigate missing rollout files before migration or backup cleanup.\n");
        }
        if (!state.workspaces().isEmpty()) {
            String prefixes = state.workspaces().stream()
                    .map(workspace -> pathPrefix(workspace.path()))
                    .distinct()
                    .limit(5)
                    .collect(Collectors.joining("`, `", "`", "`"));
            report.append("- For migration, run `migrate plan` first. Observed candidate prefixes: ").append(prefixes).append(".\n");
        }
        report.append("- Keep Codex closed before running commands that apply repairs or migrations.\n");
    }

    private static long coalesce(Long... values) {
        for (Long value : values) {
            if (value != null) {
                return value;
            }
        }
        return 0L;
    }

    private static String pathPrefix(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String[] parts = path.split("/");
        if (parts.length >= 3 && parts[0].isBlank()) {
            return "/" + parts[1] + "/" + parts[2];
        }
        return path;
    }

    private static String healthLabel(DoctorReport doctor) {
        if (!doctor.healthy()) {
            return "needs attention";
        }
        return doctor.warnings().isEmpty() ? "healthy" : "healthy with warnings";
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("|", "\\|").replace("\n", " ");
    }
}
