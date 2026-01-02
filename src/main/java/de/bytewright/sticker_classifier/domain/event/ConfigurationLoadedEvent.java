package de.bytewright.sticker_classifier.domain.event;

import de.bytewright.sticker_classifier.domain.model.ClassificationCategory;
import de.bytewright.sticker_classifier.domain.model.CompoundClassificationCategory;
import java.nio.file.Path;
import java.util.List;
import lombok.Builder;

@Builder
public record ConfigurationLoadedEvent(
    Path workDirectory,
    Path outputDirectory,
    List<ClassificationCategory> classifications,
    List<CompoundClassificationCategory> compoundCategories) {}
