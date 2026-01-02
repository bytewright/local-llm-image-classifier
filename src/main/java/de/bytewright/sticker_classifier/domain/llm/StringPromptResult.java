package de.bytewright.sticker_classifier.domain.llm;

import java.util.UUID;

public record StringPromptResult(
    PromptRequest request, PromptType type, UUID requestParameter, String result)
    implements PromptResult {}
