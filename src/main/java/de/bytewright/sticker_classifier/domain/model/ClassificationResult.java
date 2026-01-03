package de.bytewright.sticker_classifier.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ClassificationResult {
  @JsonProperty(required = true)
  @Nonnull
  private Set<String> detectedTags;

  @JsonProperty(required = true)
  @Nonnull
  private boolean hasText;

  @JsonProperty(required = true)
  @Nullable
  private String textLanguageGuess;

  @JsonProperty(required = true)
  @Nullable
  private String emoji;

  @JsonProperty(required = true)
  @Nullable
  private String keyword;
}
