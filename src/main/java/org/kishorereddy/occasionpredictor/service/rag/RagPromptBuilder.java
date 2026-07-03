package org.kishorereddy.occasionpredictor.service.rag;

import org.kishorereddy.occasionpredictor.model.PredictionRequest;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RagPromptBuilder {

    /**
     * Assembles the final prompt sent to the LLM:
     *   1. System instructions (from DB prompt_versions or the hardcoded default)
     *   2. Retrieved context chunks from pgvector (omitted when the store is empty)
     *   3. Order details from the incoming request
     */
    public String build(String systemInstructions, List<Document> context, PredictionRequest request) {
        StringBuilder sb = new StringBuilder(systemInstructions.trim());

        appendContext(sb, context);
        appendOrderDetails(sb, request);

        return sb.toString();
    }

    private void appendContext(StringBuilder sb, List<Document> context) {
        if (context.isEmpty()) return;

        sb.append("\n\n## Retrieved Context Rules:\n");
        for (int i = 0; i < context.size(); i++) {
            Document doc = context.get(i);
            Object section = doc.getMetadata().get("section");

            sb.append("\n### Rule ").append(i + 1);
            if (section != null && !section.toString().isBlank()) {
                sb.append(" — ").append(section);
            }
            sb.append(":\n").append(doc.getText().trim()).append("\n");
        }
    }

    private void appendOrderDetails(StringBuilder sb, PredictionRequest request) {
        sb.append("\n\n## Order Details:\n");
        sb.append("- Recipient Name: ").append(or(request.recipientName())).append("\n");
        sb.append("- Relation: ").append(or(request.recipientRelation())).append("\n");
        sb.append("- Product: ").append(or(request.productName()));
        if (request.productCategory() != null && !request.productCategory().isBlank()) {
            sb.append(" (").append(request.productCategory()).append(")");
        }
        sb.append("\n");
        sb.append("- Order Date: ").append(or(request.orderDate())).append("\n");
        sb.append("- Gift Message: ").append(or(request.giftMessage())).append("\n");
    }

    private String or(String s) {
        return (s != null && !s.isBlank()) ? s : "N/A";
    }
}
