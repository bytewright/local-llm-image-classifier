package de.bytewright.sticker_classifier.adapter.storageInMemory;

import de.bytewright.sticker_classifier.domain.llm.PromptRequest;
import de.bytewright.sticker_classifier.domain.model.ClassificationCategory;
import de.bytewright.sticker_classifier.domain.model.ClassificationResult;
import de.bytewright.sticker_classifier.domain.session.ProcessingState;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.Data;

@Data
public class ClassificationSession {
  private final List<ClassificationCategory> classifications;
  private final Collection<ClassificationResult> results = new ArrayList<>();
  private final Path workDirectory;
  private final Path outputDirectory;
  private final Collection<PromptRequest> requests = new ArrayList<>();
  private ProcessingState processingState;
}
