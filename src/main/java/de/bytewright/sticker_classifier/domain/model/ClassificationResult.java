package de.bytewright.sticker_classifier.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ClassificationResult {
  @JsonProperty(required = true)
  private String categoryName;

  @JsonProperty(required = true)
  private boolean hasText;

  private String textLanguageGuess;
    private String emoji;
    private String keyword;
}