package de.bytewright.sticker_classifier.domain.session;

public enum ProcessingState {
  IDLE,
  DISCOVERING,
  CLASSIFYING,
  COMPLETED,
  FAILED
}
