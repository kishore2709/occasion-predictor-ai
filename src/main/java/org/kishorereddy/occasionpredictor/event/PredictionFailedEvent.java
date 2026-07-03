package org.kishorereddy.occasionpredictor.event;

import java.time.Instant;

/**
 * Published to {@code prediction.failed} when the LLM workflow throws.
 * {@code originalRequest} is embedded so {@code RetryConsumer} can replay
 * the full prediction without a separate DB lookup.
 * {@code attemptCount} is incremented on each retry hop (0 = first failure).
 */
public record PredictionFailedEvent(
        String eventId,
        String orderId,
        String errorMessage,
        int attemptCount,
        PredictionRequestedEvent originalRequest,
        Instant occurredAt
) {}
