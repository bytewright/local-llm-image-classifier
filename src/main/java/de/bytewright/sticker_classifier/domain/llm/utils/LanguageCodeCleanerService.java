package de.bytewright.sticker_classifier.domain.llm.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class LanguageCodeCleanerService {

  private static final Map<String, String> LANGUAGE_MAP = new HashMap<>();
  private static final Pattern INVALID_FILENAME_CHARS = Pattern.compile("[^a-zA-Z0-9._-]");

  static {
    // Common languages
    LANGUAGE_MAP.put("english", "en");
    LANGUAGE_MAP.put("spanish", "es");
    LANGUAGE_MAP.put("french", "fr");
    LANGUAGE_MAP.put("german", "de");
    LANGUAGE_MAP.put("italian", "it");
    LANGUAGE_MAP.put("portuguese", "pt");
    LANGUAGE_MAP.put("dutch", "nl");
    LANGUAGE_MAP.put("russian", "ru");
    LANGUAGE_MAP.put("chinese", "zh");
    LANGUAGE_MAP.put("japanese", "ja");
    LANGUAGE_MAP.put("korean", "ko");
    LANGUAGE_MAP.put("arabic", "ar");
    LANGUAGE_MAP.put("hindi", "hi");
    LANGUAGE_MAP.put("polish", "pl");
    LANGUAGE_MAP.put("turkish", "tr");
    LANGUAGE_MAP.put("swedish", "sv");
    LANGUAGE_MAP.put("norwegian", "no");
    LANGUAGE_MAP.put("danish", "da");
    LANGUAGE_MAP.put("finnish", "fi");
    LANGUAGE_MAP.put("greek", "el");
    LANGUAGE_MAP.put("czech", "cs");
    LANGUAGE_MAP.put("hungarian", "hu");
    LANGUAGE_MAP.put("romanian", "ro");
    LANGUAGE_MAP.put("thai", "th");
    LANGUAGE_MAP.put("vietnamese", "vi");
    LANGUAGE_MAP.put("indonesian", "id");
    LANGUAGE_MAP.put("malay", "ms");
    LANGUAGE_MAP.put("hebrew", "he");
    LANGUAGE_MAP.put("ukrainian", "uk");

    // Add more as needed
  }

  /**
   * Cleans and converts LLM language response to ISO code
   *
   * @param llmResponse The raw response from LLM
   * @return ISO language code (2-letter) or "int" for unknown/international
   */
  public String cleanLanguageResponse(String llmResponse) {
    if (llmResponse == null || llmResponse.trim().isEmpty()) {
      return "int";
    }

    // Normalize: lowercase and remove extra whitespace
    String normalized = llmResponse.trim().toLowerCase();

    // Check for "unknown" or similar patterns
    if (normalized.contains("unknown")
        || normalized.contains("unclear")
        || normalized.contains("unsure")
        || normalized.contains("could be")) {
      return "int";
    }

    // Try direct mapping first
    if (LANGUAGE_MAP.containsKey(normalized)) {
      return LANGUAGE_MAP.get(normalized);
    }

    // If it's already a 2-letter code, validate and return
    if (normalized.matches("^[a-z]{2}$")) {
      return normalized;
    }

    // Try to extract language name from phrases like "looks like spanish"
    for (Map.Entry<String, String> entry : LANGUAGE_MAP.entrySet()) {
      if (normalized.contains(entry.getKey())) {
        return entry.getValue();
      }
    }

    // If nothing matches, return international
    return "int";
  }

  /**
   * Sanitizes string for use in filenames
   *
   * @param input The string to sanitize
   * @return Filename-safe string
   */
  public String sanitizeForFilename(String input) {
    if (input == null || input.isEmpty()) {
      return "unknown";
    }

    // Replace invalid chars with underscore
    String sanitized = INVALID_FILENAME_CHARS.matcher(input).replaceAll("_");

    // Remove leading/trailing underscores and dots
    sanitized = sanitized.replaceAll("^[._]+|[._]+$", "");

    // Collapse multiple underscores
    sanitized = sanitized.replaceAll("_{2,}", "_");

    return sanitized.isEmpty() ? "unknown" : sanitized;
  }

  /**
   * Combined method: cleans language response and makes it filename-safe
   *
   * @param llmResponse The raw LLM response
   * @return Sanitized ISO code suitable for filenames
   */
  public String processLanguageForFilename(String llmResponse) {
    String isoCode = cleanLanguageResponse(llmResponse);
    return sanitizeForFilename(isoCode);
  }
}
