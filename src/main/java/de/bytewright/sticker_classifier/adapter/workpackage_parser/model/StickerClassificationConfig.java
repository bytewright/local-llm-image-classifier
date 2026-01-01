package de.bytewright.sticker_classifier.adapter.workpackage_parser.model;

import lombok.Data;

import java.util.List;

@Data
public class StickerClassificationConfig {
    private String workDirectory;
    private String outputDirectory;
    private List<Classification> classifications;

    @Data
    public static class Classification {
        private String name;
        private String description;
    }
}