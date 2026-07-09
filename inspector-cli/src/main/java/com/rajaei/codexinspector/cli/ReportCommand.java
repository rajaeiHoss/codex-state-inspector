package com.rajaei.codexinspector.cli;

import com.rajaei.codexinspector.report.MarkdownReportService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "report", mixinStandardHelpOptions = true, description = "Generate a Markdown Codex state audit report.")
public final class ReportCommand implements Callable<Integer> {
    @Option(names = "--source", required = true, description = "Codex state directory, for example ~/.codex")
    String source;

    @Option(names = "--output", required = true, description = "Output Markdown report path")
    String output;

    @Override
    public Integer call() throws Exception {
        Path outputPath = PathOptions.expand(output).toAbsolutePath().normalize();
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }
        String markdown = new MarkdownReportService().generate(PathOptions.expand(source));
        Files.writeString(outputPath, markdown);
        System.out.println("Report written: " + outputPath);
        return 0;
    }
}
