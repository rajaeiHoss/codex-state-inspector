package com.rajaei.codexinspector.core.domain;

import java.util.Set;

public record RolloutSummary(
        String threadId,
        String rolloutPath,
        long records,
        long userMessages,
        long assistantMessages,
        long toolCalls,
        long commands,
        Set<String> filesReferenced,
        String firstUserMessage,
        String lastUserMessage
) {
}
