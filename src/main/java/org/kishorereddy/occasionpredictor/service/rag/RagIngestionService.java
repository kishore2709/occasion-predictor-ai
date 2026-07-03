package org.kishorereddy.occasionpredictor.service.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagIngestionService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RagIngestionService.class);

    private final RagDocumentLoader loader;
    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    public RagIngestionService(RagDocumentLoader loader,
                                VectorStore vectorStore,
                                JdbcTemplate jdbcTemplate) {
        this.loader      = loader;
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vector_store", Integer.class);
            if (count != null && count > 0) {
                log.info("RAG vector store already contains {} document chunks — skipping ingestion.", count);
                return;
            }
            ingestAll();
        } catch (Exception e) {
            log.warn("RAG ingestion skipped — vector store unavailable or Ollama not reachable: {}", e.getMessage());
        }
    }

    public void ingestAll() {
        log.info("Starting RAG document ingestion...");
        try {
            List<Document> documents = loader.loadAll();
            log.info("Loaded {} chunks — generating embeddings and storing in pgvector...", documents.size());

            // PgVectorStore calls the EmbeddingModel (Ollama nomic-embed-text) internally
            vectorStore.add(documents);

            log.info("RAG ingestion complete. {} chunks stored in pgvector.", documents.size());
        } catch (Exception e) {
            log.error("RAG ingestion failed: {}", e.getMessage(), e);
            throw new RuntimeException("RAG ingestion failed", e);
        }
    }
}
