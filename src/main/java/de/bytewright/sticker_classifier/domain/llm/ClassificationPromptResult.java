package de.bytewright.sticker_classifier.domain.llm;


import de.bytewright.sticker_classifier.domain.model.ClassificationResult;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public final class ClassificationPromptResult implements PromptResult {
    private PromptRequestWithImage promptRequestWithImage;
    private ClassificationResult classificationResult;

    @Override
    public PromptRequest request() {
        return promptRequestWithImage;
    }

    @Override
    public PromptType type() {
        return PromptType.STICKER_CLASSIFICATION;
    }
}