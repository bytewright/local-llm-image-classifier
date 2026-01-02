package de.bytewright.sticker_classifier.domain.llm;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import lombok.Builder;

/**
 * Generates character meta-information (e.g., gender, age) from an image.
 *
 * @param imagePath The image file of the character.
 * @param prompt The specific question to ask about the image (e.g., "Describe the person's apparent
 *     gender and estimated age. Respond in JSON format with keys 'gender' and 'age'.").
 */
@Builder
public record PromptRequestWithImage(
        Path imagePath, String prompt, PromptType promptType, UUID requestParameter, String jsonFormat)
    implements PromptRequest {
  @Override
  public Optional<String> responseJsonFormat() {
    return Optional.ofNullable(jsonFormat);
  }
}
