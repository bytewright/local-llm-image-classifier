package de.bytewright.sticker_classifier.domain.model;


import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
public class ClassificationResult {
    private Map<String, Double> categoryScores;
    private String suggestedCategory;
    private String emoji;
    private String keyword;

    /**
     * Get the highest scoring category
     */
    public String getBestCategory() {
        return categoryScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(suggestedCategory);
    }

    /**
     * Get confidence score for the suggested category
     */
    public double getConfidence() {
        return categoryScores.getOrDefault(suggestedCategory, 0.0);
    }
}