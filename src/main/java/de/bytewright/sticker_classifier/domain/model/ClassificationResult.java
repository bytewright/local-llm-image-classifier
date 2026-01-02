package de.bytewright.sticker_classifier.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ClassificationResult {
  @JsonProperty(required = true)
  private Set<String> detectedTags;

  @JsonProperty(required = true)
  private boolean hasText;

  private String textLanguageGuess;
  private String emoji;
  private String keyword;
}
