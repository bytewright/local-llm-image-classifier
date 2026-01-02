package de.bytewright.sticker_classifier.orchestration;

import de.bytewright.sticker_classifier.domain.event.ConfigurationLoadedEvent;
import de.bytewright.sticker_classifier.domain.llm.*;
import de.bytewright.sticker_classifier.domain.model.ClassificationCategory;
import de.bytewright.sticker_classifier.domain.session.ProcessingState;
import de.bytewright.sticker_classifier.domain.storage.SessionStorage;
import de.bytewright.sticker_classifier.orchestration.llm.PromptRequestCoordinator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClassifyStickers {
  private final SessionStorage sessionStorage;
  private final PromptRequestCoordinator coordinator;
  private final FileDiscovery fileDiscovery;
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
    } catch (Exception e) {
      log.error("Error during processing", e);
      sessionStorage.updateState(sessionId, ProcessingState.FAILED);
    }
  }

  private void prepareResultDirectories(UUID sessionId) throws IOException {
    Path resultRootDir = sessionStorage.getResultRootDir(sessionId);
    Files.createDirectories(resultRootDir);
    for (ClassificationCategory classificationCategory :
        sessionStorage.getClassificationCategories(sessionId)) {
      String categoryName = classificationCategory.name();
      Path path = currentConfig.outputDirectory().resolve(categoryName);
      Files.createDirectories(path);
    }
  }

  private void classifyFiles(UUID sessionId, Collection<FileMetadata> fileMetadata) {
    log.info("Phase 2: Classifying {} unique files", fileMetadata.size());

    String classificationPrompt = buildClassificationPrompt();
    for (FileMetadata file : fileMetadata) {
      PromptRequestWithImage request =
          PromptRequestWithImage.builder()
              .imagePath(file.originalPath())
              .prompt(classificationPrompt)
              .promptType(PromptType.STICKER_CLASSIFICATION)
              .requestParameter(sessionId)
              .build();
      coordinator.schedule(request);
      sessionStorage.addRequest(sessionId, request);
    }
  }

  private String buildClassificationPrompt() {
    StringBuilder prompt = new StringBuilder();
    prompt.append("Classify this sticker image into one of the following categories:\n\n");

    for (ClassificationCategory category : currentConfig.classifications()) {
      prompt.append("Category: ").append(category.name()).append("\n");
      prompt.append("Description: ").append(category.description()).append("\n\n");
    }
    return prompt.toString();
  }

  public record FileMetadata(Path originalPath, String hash, long size) {}
}
