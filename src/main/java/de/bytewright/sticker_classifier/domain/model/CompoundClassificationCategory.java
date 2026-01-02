package de.bytewright.sticker_classifier.domain.model;

import java.util.Set;

public record CompoundClassificationCategory(
    String name, Set<ClassificationCategory> categorySet, String description) {}
