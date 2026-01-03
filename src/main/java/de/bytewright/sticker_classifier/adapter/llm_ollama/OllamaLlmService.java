package de.bytewright.sticker_classifier.adapter.llm_ollama;

import static com.github.victools.jsonschema.generator.Option.EXTRA_OPEN_API_FORMAT_VALUES;
import static com.github.victools.jsonschema.generator.OptionPreset.PLAIN_JSON;
import static com.github.victools.jsonschema.generator.SchemaVersion.DRAFT_2020_12;
import static com.github.victools.jsonschema.module.jackson.JacksonOption.RESPECT_JSONPROPERTY_REQUIRED;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import de.bytewright.sticker_classifier.domain.llm.*;
import de.bytewright.sticker_classifier.domain.model.ClassificationResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@Profile("!test")
@RequiredArgsConstructor
public class OllamaLlmService implements LlmConnector, InitializingBean {

  private static final double TOKEN_TO_CHAR_RATIO = 4.0;
  private static final double API_OVERHEAD_MARGIN = 1.2; // 20% buffer
  private final OllamaAdapterConfig ollamaAdapterConfig;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final PromptDataGenerator promptDataGenerator;
  private final ClassificationResponseParser classificationResponseParser;
  private final PromptLog promptLog;
  private final OllamaApi ollamaApi;

  @Override
  public Optional<PromptResult> processRequest(PromptRequest request) {
    log.debug(
        "Processing request of type {} for id {}",
        request.promptType(),
        request.requestParameter());
    try {
      String result;
      if (request instanceof PromptRequestWithImage requestWithImage) {
        String jsonResponse = requestWithImage(requestWithImage);
        return Optional.ofNullable(jsonResponse)
            .flatMap(json -> classificationResponseParser.parseResponse(requestWithImage, json))
            .map(
                value ->
                    ClassificationPromptResult.builder()
                        .promptRequestWithImage(requestWithImage)
                        .classificationResult(value)
                        .build());
      } else {
        String context = getContext(request);
        result = call(request, context);
      }
      var promptResult =
          new StringPromptResult(request, request.promptType(), request.requestParameter(), result);
      log.info(
          "Successfully processed request of type {} for id {}",
          request.promptType(),
          request.requestParameter());
      return Optional.of(promptResult);
    } catch (Exception e) {
      log.error(
          "Failed to process request of type {} for id {}",
          request.promptType(),
          request.requestParameter(),
          e);
    }
    return Optional.empty();
  }

  private String getContext(PromptRequest request) throws JsonProcessingException {
    return switch (request) {
      case PromptRequestUnstructured unstructured -> {
        Map<String, Object> dataMap = promptDataGenerator.toMap(unstructured.promptDataList());
        yield objectMapper.writeValueAsString(dataMap);
      }
      case PromptRetry promptRetry -> getContext(promptRetry.delegate());
      case PromptRequestWithImage requestWithImage -> null;
    };
  }

  /**
   * Internal method to call the Ollama API with text-only input.
   *
   * @param prompt The user's prompt.
   * @param context Additional context for the prompt.
   * @return The content of the Ollama API response.
   */
  String call(PromptRequest prompt, String context) {
    log.debug(
        "Sending text-only prompt to model {}:\nPrompt: {}\nContext: {}",
        ollamaAdapterConfig.getTextModel(),
        prompt.prompt(),
        context);
    promptLog.logPrompt(prompt, context);

    long estimatedTokens = estimateTokenCount(prompt.prompt(), context);
    int clampedContextSize =
        Math.clamp(
            estimatedTokens,
            ollamaAdapterConfig.getMinContextSize(),
            ollamaAdapterConfig.getMaxContextSize());
    log.info(
        "Estimated tokens: {}, Clamped context size (numCtx): {}",
        estimatedTokens,
        clampedContextSize);

    var requestBuilder =
        OllamaApi.ChatRequest.builder(ollamaAdapterConfig.getTextModel()).stream(false)
            .thinkLow()
            .messages(
                List.of(
                    OllamaApi.Message.builder(OllamaApi.Message.Role.SYSTEM)
                        .content(SystemPrompts.TEXT_ANALYZE.getPrompt())
                        .build(),
                    OllamaApi.Message.builder(OllamaApi.Message.Role.USER)
                        .content(String.format("Context for next prompt: %s", context))
                        .build(),
                    OllamaApi.Message.builder(OllamaApi.Message.Role.USER)
                        .content(prompt.prompt())
                        .build()))
            .options(
                OllamaChatOptions.builder()
                    //   .temperature(1.5)
                    //   .topP(0.95)
                    .numCtx(clampedContextSize)
                    .build());
    if (prompt.responseJsonFormat().isPresent()) {
      try {
        Object schema = objectMapper.readValue(prompt.responseJsonFormat().get(), Object.class);
        requestBuilder = requestBuilder.format(schema);
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    }
    try {
      OllamaApi.ChatResponse response = ollamaApi.chat(requestBuilder.build());
      if (response != null && response.message() != null) {
        OllamaApi.Message message = response.message();
        String content = message.content();
        promptLog.logResponse(prompt, content);
        return content;
      } else {
        log.error("Received null response or message from Ollama API for text request.");
        return "Error: No response from LLM.";
      }
    } catch (Exception e) {
      log.error("Error calling Ollama API for text request: {}", e.getMessage(), e);
      return "Error: Could not connect to LLM or process request.";
    }
  }

  /**
   * Estimates the number of tokens based on the character length of the input strings.
   *
   * @param prompt The user's prompt.
   * @param context The context for the prompt.
   * @return An estimated token count with a buffer for API overhead.
   */
  private long estimateTokenCount(String prompt, String context) {
    long totalChars = prompt.length() + (StringUtils.hasLength(context) ? context.length() : 0);
    double estimatedTokens = totalChars / TOKEN_TO_CHAR_RATIO;
    return Math.round(estimatedTokens * API_OVERHEAD_MARGIN);
  }

  private String callWithImage(PromptRequestWithImage prompt, String base64Image) {
    log.debug(
        "Sending multimodal prompt to model {}:\nPrompt: {}",
        ollamaAdapterConfig.getMultiModalModel(),
        prompt);

    promptLog.logPrompt(prompt, null);
    var userMessage =
        OllamaApi.Message.builder(OllamaApi.Message.Role.USER)
            .content(prompt.prompt())
            .images(List.of(base64Image))
            .build();

    // Define JSON schema for structured response
    var responseSchema = getSchema(ClassificationResult.class);

    double estimatedTokens =
        estimateTokenCount(prompt.prompt(), "")
            + (base64Image.length() / TOKEN_TO_CHAR_RATIO) * 1.3;
    int clampedContextSize =
        Math.clamp(
            Math.round(estimatedTokens),
            ollamaAdapterConfig.getMinContextSize(),
            ollamaAdapterConfig.getMaxContextSize());
    var request =
        OllamaApi.ChatRequest.builder(ollamaAdapterConfig.getMultiModalModel()).stream(false)
            // .thinkLow()
            .messages(
                List.of(
                    OllamaApi.Message.builder(OllamaApi.Message.Role.SYSTEM)
                        .content(SystemPrompts.IMAGE_CLASSIFY_ANALYZE.getPrompt())
                        .build(),
                    userMessage))
            .options(
                OllamaChatOptions.builder()
                    .temperature(0.3)
                    .topP(0.9)
                    .numCtx(clampedContextSize)
                    .build())
            .format(responseSchema) // Enforce JSON structure
            .build();

    try {
      OllamaApi.ChatResponse response = ollamaApi.chat(request);
      if (response != null && response.message() != null) {
        String content = response.message().content();
        promptLog.logResponse(prompt, content);
        return content;
      } else {
        log.error("Received null response or message from Ollama API for multimodal request.");
        return null;
      }
    } catch (Exception e) {
      log.error("Error calling Ollama API for multimodal request: {}", e.getMessage(), e);
      return null;
    }
  }

  Object getSchema(Class<ClassificationResult> aClass) {
    JacksonModule module = new JacksonModule(RESPECT_JSONPROPERTY_REQUIRED);
    SchemaGeneratorConfigBuilder configBuilder =
        new SchemaGeneratorConfigBuilder(DRAFT_2020_12, PLAIN_JSON)
            .with(module)
            .with(EXTRA_OPEN_API_FORMAT_VALUES);

    SchemaGenerator generator = new SchemaGenerator(configBuilder.build());
    JsonNode jsonSchema = generator.generateSchema(aClass);
    return jsonSchema;
  }

  private String requestWithImage(PromptRequestWithImage requestWithImage) {
    Path imagePath = requestWithImage.imagePath();
    log.info(
        "Attempting to get character info from image: {}",
        imagePath != null ? imagePath.toAbsolutePath() : "null");
    if (imagePath == null) {
      log.error("Image file is null.");
      return "Error: Image file cannot be null.";
    }
    try {
      String base64Image = encodeImageToBase64(imagePath);
      return callWithImage(requestWithImage, base64Image);
    } catch (IOException e) {
      log.error("Failed to encode image to Base64: {}", e.getMessage(), e);
      return "Error: Could not process image file. " + e.getMessage();
    } catch (Exception e) {
      log.error(
          "An unexpected error occurred while getting character info from image: {}",
          e.getMessage(),
          e);
      return "Error: An unexpected error occurred. " + e.getMessage();
    }
  }

  /**
   * Helper method to encode an image file to a Base64 string.
   *
   * @param imagePath The image file to encode.
   * @return A Base64 encoded string representation of the image.
   * @throws IOException If an error occurs during file reading.
   */
  private String encodeImageToBase64(Path imagePath) throws IOException {
    if (imagePath == null || !Files.exists(imagePath) || !Files.isRegularFile(imagePath)) {
      throw new IOException(
          "Image file is invalid or does not exist: "
              + (imagePath != null ? imagePath.toAbsolutePath() : "null"));
    }
    byte[] fileContent = Files.readAllBytes(imagePath);
    return Base64.getEncoder().encodeToString(fileContent);
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    objectMapper.findAndRegisterModules();
  }
}
