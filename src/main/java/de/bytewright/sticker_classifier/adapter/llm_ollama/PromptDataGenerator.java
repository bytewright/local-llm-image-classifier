package de.bytewright.sticker_classifier.adapter.llm_ollama;

import de.bytewright.sticker_classifier.domain.llm.PromtContextData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PromptDataGenerator {

    public Map<String, Object> toMap(List<PromtContextData> promtData) {
        Map<String, Object> promtDataMap = new HashMap<>();
        // todo
        return promtDataMap;
    }
}
