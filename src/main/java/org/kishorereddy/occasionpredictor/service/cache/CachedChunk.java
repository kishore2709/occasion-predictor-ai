package org.kishorereddy.occasionpredictor.service.cache;

import org.springframework.ai.document.Document;

import java.util.HashMap;
import java.util.Map;

/**
 * Serialization-safe wrapper for a Spring AI Document stored in Redis.
 * The original pgvector row ID is preserved in metadata under {@code _chunk_id}
 * so that audit records remain accurate even when the Document is reconstructed
 * from cache (which would otherwise generate a new random UUID).
 */
public record CachedChunk(String id, String text, Map<String, Object> metadata) {

    public static CachedChunk from(Document doc) {
        Map<String, Object> meta = new HashMap<>(doc.getMetadata());
        meta.put("_chunk_id", doc.getId());
        return new CachedChunk(doc.getId(), doc.getText(), meta);
    }

    public Document toDocument() {
        return new Document(text, metadata);
    }
}
