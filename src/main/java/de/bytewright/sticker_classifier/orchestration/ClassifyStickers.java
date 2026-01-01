package de.bytewright.sticker_classifier.orchestration;

import de.bytewright.sticker_classifier.domain.event.ConfigurationLoadedEvent;
import de.bytewright.sticker_classifier.domain.llm.*;
import de.bytewright.sticker_classifier.domain.model.ClassificationCategory;
import de.bytewright.sticker_classifier.domain.model.ClassificationResult;
import de.bytewright.sticker_classifier.domain.session.ProcessingState;
import de.bytewright.sticker_classifier.domain.storage.SessionStorage;
import de.bytewright.sticker_classifier.orchestration.llm.PromptRequestCoordinator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClassifyStickers {
  private final SessionStorage sessionStorage;
  private final PromptRequestCoordinator coordinator;
  private final FileDiscovery fileDiscovery;

  private final Map<String, FileMetadata> filesByHash = new ConcurrentHashMap<>();
  private final Queue<Path> processingQueue = new LinkedList<>();

  private volatile ProcessingState state = ProcessingState.IDLE;
  private volatile int totalFiles = 0;
  private volatile int processedFiles = 0;
  private ConfigurationLoadedEvent currentConfig;

  @EventListener
  public void handleConfigurationLoaded(ConfigurationLoadedEvent event) {
    log.info("Configuration event received!");
    log.info("Work directory: {}", event.workDirectory());
    log.info("Output directory: {}", event.outputDirectory());
    log.info("Classifications: {}", event.classifications().size());
    UUID sessionId =
        sessionStorage.createSession(
            event.classifications(), event.workDirectory(), event.outputDirectory());
    this.currentConfig = event;
    startProcessing(sessionId);
  }

  private void startProcessing(UUID sessionId) {
    log.info("Starting sticker classification pipeline for sessionId {}", sessionId);
    sessionStorage.updateState(sessionId, ProcessingState.DISCOVERING);
    try {
      // Phase 1: Discovery and Deduplication
      Collection<FileMetadata> fileMetadata = fileDiscovery.discoverUniqueFiles(sessionId);
      // Phase 2: result preparation
      prepareResultDirectories(sessionId);
      // Phase 3: Classification
      sessionStorage.updateState(sessionId, ProcessingState.CLASSIFYING);
      classifyFiles(sessionId, fileMetadata);

      // Phase 3: Complete
      state = ProcessingState.COMPLETED;
      logCompletionSummary();

    } catch (Exception e) {
      log.error("Error during processing", e);
      state = ProcessingState.FAILED;
    }
  }

  private void prepareResultDirectories(UUID sessionId) throws IOException {
    Path resultRootDir = sessionStorage.getResultRootDir(sessionId);
    Files.createDirectories(resultRootDir);
    for (ClassificationCategory classificationCategory :
        sessionStorage.getClassificationCategories(sessionId)) {
      String categoryName = classificationCategory.getName();
      Path path = currentConfig.outputDirectory().resolve(categoryName);
      Files.createDirectories(path);
    }
  }

  private void classifyFiles(UUID sessionId, Collection<FileMetadata> fileMetadata) {
    log.info("Phase 2: Classifying {} unique files", processingQueue.size());

    String classificationPrompt = buildClassificationPrompt();
    String jsonSchema = buildJsonSchema();
    for (FileMetadata file : fileMetadata) {
      PromptRequestWithImage request =
          PromptRequestWithImage.builder()
              .imagePath(file.originalPath())
              .prompt(classificationPrompt)
              .promptType(PromptType.STICKER_CLASSIFICATION)
              .requestParameter(sessionId)
              .jsonFormat(jsonSchema)
              .build();
      coordinator.schedule(request);
    }
  }

  private String buildClassificationPrompt() {
    StringBuilder prompt = new StringBuilder();
    prompt.append("Classify this sticker image into one of the following categories:\n\n");

    for (ClassificationCategory category : currentConfig.classifications()) {
      prompt.append("Category: ").append(category.getName()).append("\n");
      prompt.append("Description: ").append(category.getDescription()).append("\n\n");
    }

    prompt.append("Provide a confidence score (0.0 to 1.0) for EACH category listed above.\n");
    prompt.append("Also suggest an emoji and a short keyword that represents this sticker.");

    return prompt.toString();
  }

  private String buildJsonSchema() {
    // Build the schema with all category names
    Map<String, Object> categoryScoresSchema = new HashMap<>();
    for (ClassificationCategory category : currentConfig.classifications()) {
      categoryScoresSchema.put(category.getName(), Map.of("type", "number"));
    }

    return """
                {
                  "type": "object",
                  "properties": {
                    "categoryScores": {
                      "type": "object",
                      "properties": %s
                    },
                    "suggestedCategory": {"type": "string"},
                    "emoji": {"type": "string"},
                    "keyword": {"type": "string"}
                  },
                  "required": ["categoryScores", "suggestedCategory", "emoji", "keyword"]
                }
                """
        .formatted(toJsonString(categoryScoresSchema));
  }

  private String toJsonString(Map<String, Object> map) {
    // Simple JSON serialization for schema
    StringBuilder sb = new StringBuilder("{");
    map.forEach(
        (key, value) -> {
          sb.append("\"").append(key).append("\":").append(value).append(",");
        });
    if (sb.length() > 1) sb.setLength(sb.length() - 1); // Remove trailing comma
    sb.append("}");
    return sb.toString();
  }

  private void logCompletionSummary() throws IOException {
    log.info("=== Classification Complete ===");
    log.info("Total files scanned: {}", totalFiles);
    log.info("Unique files: {}", filesByHash.size());
    log.info("Duplicates removed: {}", totalFiles - filesByHash.size());
    log.info("Files classified: {}", processedFiles);

    // Statistics by category
    Map<String, Long> categoryStats =
        filesByHash.values().stream()
            .filter(fm -> fm.classificationResult != null)
            .collect(
                Collectors.groupingBy(
                    fm -> fm.classificationResult.getSuggestedCategory(), Collectors.counting()));

    log.info("Classification breakdown:");
    categoryStats.forEach((category, count) -> log.info("  - {}: {} files", category, count));
    Set<String> categories =
        filesByHash.values().stream()
            .filter(fm -> fm.classificationResult != null)
            .map(fileMetadata -> fileMetadata.classificationResult.getSuggestedCategory())
            .collect(Collectors.toSet());
    Map<String, Path> categoryPaths = new HashMap<>();
    Files.createDirectories(currentConfig.outputDirectory());
    for (String category : categories) {
      Path path = currentConfig.outputDirectory().resolve(category);
      categoryPaths.put(category, path);
      Files.createDirectories(path);
    }
    for (FileMetadata fm : filesByHash.values()) {
      Path orgPath = fm.originalPath;
      ClassificationResult result = fm.classificationResult;
      if (result == null) continue;

    }
  }

  // Getters for web interface
  public ProcessingState getState() {
    return state;
  }

  public int getTotalFiles() {
    return totalFiles;
  }

  public int getProcessedFiles() {
    return processedFiles;
  }

  public double getProgress() {
    return filesByHash.size() > 0 ? (double) processedFiles / filesByHash.size() * 100.0 : 0.0;
  }

  public Map<String, List<FileMetadata>> getFilesByCategory() {
    return filesByHash.values().stream()
        .filter(fm -> fm.classificationResult != null)
        .collect(Collectors.groupingBy(fm -> fm.classificationResult.getSuggestedCategory()));
  }

  public Collection<FileMetadata> getAllFiles() {
    return filesByHash.values();
  }

  public record FileMetadata(Path originalPath, String hash, long size) {}
}
