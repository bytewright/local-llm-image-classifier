package de.bytewright.sticker_classifier;

import de.bytewright.sticker_classifier.domain.AppOrchestrationConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppOrchestrationConfig.class)
public class StickerClassifierApplication {

  public static void main(String[] args) {
    SpringApplication.run(StickerClassifierApplication.class, args);
  }
}
