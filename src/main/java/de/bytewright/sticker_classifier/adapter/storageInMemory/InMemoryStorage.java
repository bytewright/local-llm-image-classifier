package de.bytewright.sticker_classifier.adapter.storageInMemory;

import de.bytewright.sticker_classifier.domain.event.ConfigurationLoadedEvent;
import de.bytewright.sticker_classifier.domain.model.ClassificationCategory;
import de.bytewright.sticker_classifier.domain.model.ClassificationResult;
import de.bytewright.sticker_classifier.domain.session.ProcessingState;
import de.bytewright.sticker_classifier.domain.storage.SessionStorage;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class InMemoryStorage implements SessionStorage {
  private final ConcurrentMap<UUID, ClassificationSession> storage = new ConcurrentHashMap<>();

  @Override
  public UUID createSession(
      List<ClassificationCategory> classifications, Path workDirectory, Path outputDirectory) {
    ClassificationSession session =
        new ClassificationSession(classifications, workDirectory, outputDirectory);
    UUID sessionId = UUID.randomUUID();
    storage.put(sessionId, session);
    return sessionId;
  }

  @Override
  public void updateState(UUID sessionId, ProcessingState processingState) {
    getOrThrow(sessionId).setProcessingState(processingState);
  }

  @Override
  public Path getWorkDir(UUID sessionId) {
    return getOrThrow(sessionId).getWorkDirectory();
  }

  @Override
  public Path getResultRootDir(UUID sessionId) {
    return getOrThrow(sessionId).getOutputDirectory();
  }

  @Override
  public List<ClassificationCategory> getClassificationCategories(UUID sessionId) {
    return getOrThrow(sessionId).getClassifications();
  }

  @Override
  public void storeResult(UUID sessionId, ClassificationResult result) {
    getOrThrow(sessionId).getResults().add(result);
  }

  private ClassificationSession getOrThrow(UUID sessionId) {
    return Optional.ofNullable(storage.get(sessionId)).orElseThrow();
  }
}
