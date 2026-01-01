package de.bytewright.sticker_classifier.adapter.llm_ollama;


import com.fasterxml.jackson.databind.ObjectMapper;
import de.bytewright.sticker_classifier.domain.model.ClassificationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClassificationResponseParser {

    private final ObjectMapper objectMapper;

    /**
     * Parse LLM response into ClassificationResult
     * @param jsonResponse The raw JSON response from the LLM
     * @return Parsed ClassificationResult or empty if parsing fails
     */
    public Optional<ClassificationResult> parseResponse(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.isBlank()) {
            log.warn("Received empty response from LLM");
            return Optional.empty();
        }

        try {
            // Clean response - remove potential markdown code blocks
            String cleanedJson = cleanJsonResponse(jsonResponse);

            ClassificationResult result = objectMapper.readValue(
                    cleanedJson,
                    ClassificationResult.class
            );

            // Validate the result
            if (isValidResult(result)) {
                log.debug("Successfully parsed classification result: {}", result.getSuggestedCategory());
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

    /**
     * Clean JSON response by removing markdown code blocks and extra whitespace
     */
    private String cleanJsonResponse(String response) {
        return response
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();
    }

    /**
     * Validate that the classification result has required fields
     */
    private boolean isValidResult(ClassificationResult result) {
        if (result == null) {
            return false;
        }

        if (result.getCategoryScores() == null || result.getCategoryScores().isEmpty()) {
            log.warn("No category scores provided");
            return false;
        }

        if (result.getSuggestedCategory() == null || result.getSuggestedCategory().isBlank()) {
            log.warn("No suggested category provided");
            return false;
        }

        if (!result.getCategoryScores().containsKey(result.getSuggestedCategory())) {
            log.warn("Suggested category '{}' not found in scores", result.getSuggestedCategory());
            return false;
        }

        // Emoji and keyword are optional but log if missing
        if (result.getEmoji() == null || result.getEmoji().isBlank()) {
            log.debug("No emoji provided for classification");
        }

        if (result.getKeyword() == null || result.getKeyword().isBlank()) {
            log.debug("No keyword provided for classification");
        }

        return true;
    }
}
