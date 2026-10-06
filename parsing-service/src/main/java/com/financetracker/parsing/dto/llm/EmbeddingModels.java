package com.financetracker.parsing.dto.llm;

import java.util.List;

/**
 * Request/response shapes for the OpenAI-compatible {@code /v1/embeddings}
 * endpoint - again, supported by local servers (Ollama) and hosted
 * providers alike, keeping the local-vs-API choice a config toggle.
 */
public class EmbeddingModels {

    public record EmbeddingRequest(String model, String input) {
    }

    public record EmbeddingResponse(List<EmbeddingData> data) {

        public float[] firstEmbedding() {
            if (data == null || data.isEmpty()) {
                throw new IllegalStateException("Embedding response contained no data");
            }
            List<Double> raw = data.get(0).embedding();
            float[] result = new float[raw.size()];
            for (int i = 0; i < raw.size(); i++) {
                result[i] = raw.get(i).floatValue();
            }
            return result;
        }
    }

    public record EmbeddingData(List<Double> embedding) {
    }

    private EmbeddingModels() {
        // Namespace holder only - never instantiated.
    }
}
