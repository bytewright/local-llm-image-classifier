package de.bytewright.sticker_classifier.domain.event;

import de.bytewright.sticker_classifier.domain.model.ClassificationCategory;
import lombok.Value;

import java.nio.file.Path;
import java.util.List;

public record ConfigurationLoadedEvent(Path workDirectory, Path outputDirectory, List<ClassificationCategory> classifications) {
}
