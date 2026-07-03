package org.kishorereddy.occasionpredictor.service.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class RagDocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(RagDocumentLoader.class);

    @Value("${app.rag.chunk-size:800}")
    private int chunkSize;

    @Value("${app.rag.chunk-overlap:100}")
    private int chunkOverlap;

    private final ObjectMapper objectMapper;

    public RagDocumentLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<Document> loadAll() throws IOException {
        List<Document> documents = new ArrayList<>();
        documents.addAll(loadMarkdown("rag-docs/occasion_rules.md",           "occasion_rules"));
        documents.addAll(loadMarkdown("rag-docs/gift_category_rules.md",      "gift_category_rules"));
        documents.addAll(loadMarkdown("rag-docs/recipient_relation_rules.md", "recipient_relation_rules"));
        documents.addAll(loadMarkdown("rag-docs/brand_rules.md",              "brand_rules"));
        documents.addAll(loadHolidayCalendar("rag-docs/holiday_calendar.json"));
        log.info("Loaded {} total chunks from {} RAG documents.", documents.size(), 5);
        return documents;
    }

    // ── Markdown loader ──────────────────────────────────────────────────────

    private List<Document> loadMarkdown(String resourcePath, String category) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        String fullText = resource.getContentAsString(StandardCharsets.UTF_8);
        String docName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);

        // Split on H2 headings (## ) using a lookahead so the delimiter stays with each section
        String[] sections = fullText.split("(?=\n## )");

        List<Document> docs = new ArrayList<>();
        for (String section : sections) {
            String trimmed = section.trim();
            if (trimmed.isEmpty()) continue;

            // H1 lines are document titles — skip them as standalone sections
            if (trimmed.startsWith("# ") && !trimmed.startsWith("## ")) continue;

            // Extract H2 heading as the section name
            String heading = "";
            int newlineIdx = trimmed.indexOf('\n');
            if (trimmed.startsWith("## ")) {
                heading = (newlineIdx > 0 ? trimmed.substring(3, newlineIdx) : trimmed.substring(3)).trim();
                trimmed = newlineIdx > 0 ? trimmed.substring(newlineIdx).trim() : "";
            }

            if (trimmed.isEmpty()) continue;

            List<String> chunks = chunk(trimmed);
            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Object> metadata = buildMetadata(
                        docName, category, heading, "global", "generic",
                        category + "_" + slug(heading) + "_" + i);
                docs.add(new Document(chunks.get(i), metadata));
            }
        }

        log.debug("  {} → {} chunks (category={})", docName, docs.size(), category);
        return docs;
    }

    // ── JSON holiday calendar loader ─────────────────────────────────────────

    private List<Document> loadHolidayCalendar(String resourcePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        JsonNode root = objectMapper.readTree(resource.getInputStream());
        String docName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);

        List<Document> docs = new ArrayList<>();
        for (JsonNode h : root.path("holidays")) {
            String occasion = h.path("occasion").asText("");
            String name     = h.path("name").asText("");
            String country  = h.path("country").asText("global");
            String desc     = h.path("description").asText("");
            int leadDays    = h.path("lead_days").asInt(14);

            String content = name + " (" + occasion + "): " + desc
                    + " Lead time for gift orders: " + leadDays + " days before the holiday."
                    + " Country: " + country + ".";

            String ruleId = "holiday_" + occasion.toLowerCase() + "_" + country.toLowerCase();
            Map<String, Object> metadata = buildMetadata(
                    docName, "holiday_calendar", name, country, "generic", ruleId);
            docs.add(new Document(content, metadata));
        }

        log.debug("  {} → {} chunks (category=holiday_calendar)", docName, docs.size());
        return docs;
    }

    // ── Chunking ─────────────────────────────────────────────────────────────

    private List<String> chunk(String text) {
        if (text.length() <= chunkSize) return List.of(text);

        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());

            // Prefer breaking at a sentence or paragraph boundary near the end of the window
            if (end < text.length()) {
                int lastNewline = text.lastIndexOf('\n', end);
                int lastPeriod  = text.lastIndexOf(". ", end);
                int boundary    = Math.max(lastNewline, lastPeriod);
                if (boundary > start + chunkSize / 2) {
                    end = boundary + 1;
                }
            }

            String piece = text.substring(start, end).trim();
            if (!piece.isBlank()) result.add(piece);

            if (end >= text.length()) break;
            start = end - chunkOverlap;
        }
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> buildMetadata(String docName, String category,
                                               String section, String country,
                                               String brand, String ruleId) {
        Map<String, Object> m = new HashMap<>();
        m.put("docName",  docName);
        m.put("category", category);
        m.put("section",  section);
        m.put("country",  country);
        m.put("brand",    brand);
        m.put("ruleId",   ruleId);
        return m;
    }

    private String slug(String text) {
        return text.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("_+$", "");
    }
}
