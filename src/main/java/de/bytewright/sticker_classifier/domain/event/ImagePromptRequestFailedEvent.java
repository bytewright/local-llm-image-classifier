package de.bytewright.sticker_classifier.domain.event;

import java.nio.file.Path;

public record ImagePromptRequestFailedEvent(Path imagePath) {}
