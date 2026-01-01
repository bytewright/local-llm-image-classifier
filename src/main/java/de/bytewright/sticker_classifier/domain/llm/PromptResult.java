package de.bytewright.sticker_classifier.domain.llm;

public sealed interface PromptResult permits ClassificationPromptResult, StringPromptResult {
    PromptRequest request();
    PromptType type();
}
