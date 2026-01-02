package de.bytewright.sticker_classifier.adapter.llm_ollama;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.bytewright.sticker_classifier.domain.llm.PromptRequestWithImage;
import de.bytewright.sticker_classifier.domain.model.ClassificationResult;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClassificationResponseParser {

  private final ObjectMapper objectMapper;

  /**
   * Parse LLM response into ClassificationResult
   *
   * @param request
   * @param jsonResponse The raw JSON response from the LLM
   * @return Parsed ClassificationResult or empty if parsing fails
   */
  public Optional<ClassificationResult> parseResponse(
      PromptRequestWithImage request, String jsonResponse) {
    if (jsonResponse == null || jsonResponse.isBlank()) {
      log.warn("Received empty response from LLM");
      return Optional.empty();
    }

    try {
      // Clean response - remove potential markdown code blocks
      String cleanedJson = cleanJsonResponse(jsonResponse);

      ClassificationResult result = objectMapper.readValue(cleanedJson, ClassificationResult.class);

      // Validate the result
      if (isValidResult(request, result)) {
        log.debug("Successfully parsed classification result: {}", result.getDetectedTags());
        return Optional.of(result);
      } else {
        log.warn("Parsed result failed validation: {}", result);
        return Optional.empty();
      }

    } catch (Exception e) {
      log.error("Failed to parse LLM response: {}", jsonResponse, e);
      return Optional.empty();
    }
  }

  /** Clean JSON response by removing markdown code blocks and extra whitespace */
  private String cleanJsonResponse(String response) {
    return response.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
  }

  /** Validate that the classification result has required fields */
  private boolean isValidResult(PromptRequestWithImage request, ClassificationResult result) {
    if (result == null) {
      return false;
    }
    if (result.getDetectedTags() == null || result.getDetectedTags().isEmpty()) {
      log.debug("LLM detected no tags for image: {}", request.imagePath());
      return false;
    } else {
      Set<String> cleanedTags =
          result.getDetectedTags().stream()
              .map(String::trim)
              .map(String::toLowerCase)
              .collect(Collectors.toSet());
      result.getDetectedTags().clear();
      result.getDetectedTags().addAll(cleanedTags);
    }

    if (result.getEmoji() == null || result.getEmoji().isBlank()) {
      log.debug("No emoji provided for classification");
    }

    if (result.getKeyword() == null || result.getKeyword().isBlank()) {
      log.debug("No keyword provided for classification");
    }
    if (result.isHasText() && !StringUtils.hasText(result.getTextLanguageGuess())) {
      log.debug("has Text but no language guess.");
      result.setHasText(false);
    }

    if (result.isHasText()
        && StringUtils.hasText(result.getTextLanguageGuess())
        && result.getTextLanguageGuess().toLowerCase().contains("unknown")) {
      result.setHasText(false);
    }
    return true;
  }
}
