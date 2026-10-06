package com.financetracker.parsing.service;

/**
 * Abstraction over "turn a piece of text into a vector embedding" -
 * separate from {@link LlmParsingClient} because chat and embedding
 * calls are conceptually and often physically (different endpoints/
 * models) distinct operations. See {@link OpenAiCompatibleEmbeddingClient}
 * for the concrete implementation.
 */
public interface EmbeddingClient {

    /** Returns the embedding vector for the given text. */
    float[] embed(String text);

    /** Which embedding model produced the vector, stored alongside it for future re-embedding decisions. */
    String modelName();
}
