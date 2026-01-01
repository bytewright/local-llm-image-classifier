package de.bytewright.sticker_classifier.adapter.storageInMemory;

import de.bytewright.sticker_classifier.domain.model.ClassificationCategory;
import de.bytewright.sticker_classifier.domain.model.ClassificationResult;
import de.bytewright.sticker_classifier.domain.session.ProcessingState;
import lombok.Data;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Data
public class ClassificationSession {
 private final List<ClassificationCategory> classifications;
  private final List<ClassificationResult> results = new ArrayList<>();
 private final Path workDirectory;
 private final Path outputDirectory;
 private ProcessingState processingState;
}
