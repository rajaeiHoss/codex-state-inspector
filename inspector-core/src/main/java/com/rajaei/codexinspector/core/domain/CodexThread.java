package com.rajaei.codexinspector.core.domain;

public record CodexThread(
        String id,
        String title,
        String cwd,
        String rolloutPath,
        String gitInfo,
        String model,
        String reasoningEffort,
        Long tokensUsed,
        boolean archived,
        Long createdAtMs,
        Long updatedAtMs,
        Long recencyAtMs
) {
}
