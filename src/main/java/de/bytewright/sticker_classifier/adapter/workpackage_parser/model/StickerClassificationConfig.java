package de.bytewright.sticker_classifier.adapter.workpackage_parser.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
public class StickerClassificationConfig {
  private String workDirectory;
  private String outputDirectory;
  private List<Classification> classifications;

  @Data
  @Builder
  @Jacksonized
  public static class Classification {
    @Builder.Default private String name = "";
    @Builder.Default private List<String> compoundCategory = new ArrayList<>();
    @Builder.Default private int priority = 0;
    @Builder.Default private String description = "";
  }
}
