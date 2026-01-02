package de.bytewright.sticker_classifier.adapter.storageInMemory;

import de.bytewright.sticker_classifier.domain.llm.PromptRequest;
import de.bytewright.sticker_classifier.domain.model.ClassificationCategory;
import de.bytewright.sticker_classifier.domain.model.ClassificationResult;
import de.bytewright.sticker_classifier.domain.session.ProcessingState;
import de.bytewright.sticker_classifier.domain.storage.SessionStorage;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
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
    ClassificationSession session = getOrThrow(sessionId);
    Collection<ClassificationResult> results = session.getResults();
    results.add(result);
    if (session.getRequests().size() == results.size()) {
      log.info("Processed all requests from session, got {} results!", results.size());
    }
  }

  @Override
  public void addRequest(UUID sessionId, PromptRequest request) {
    getOrThrow(sessionId).getRequests().add(request);
  }

  private ClassificationSession getOrThrow(UUID sessionId) {
    return Optional.ofNullable(storage.get(sessionId)).orElseThrow();
  }
}
