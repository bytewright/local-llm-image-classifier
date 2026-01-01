package de.bytewright.sticker_classifier.domain.llm;

import java.util.UUID;

public record StringPromptResult(PromptType promptType, UUID requestParameter, String result) implements PromptResult {}
