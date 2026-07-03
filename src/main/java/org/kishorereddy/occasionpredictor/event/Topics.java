package org.kishorereddy.occasionpredictor.event;

public final class Topics {
    public static final String PREDICTION_REQUESTED = "prediction.requested";
    public static final String OCCASION_PREDICTED   = "occasion.predicted";
    public static final String PREDICTION_FAILED    = "prediction.failed";
    public static final String PREDICTION_RETRY     = "prediction.retry";
    public static final String PREDICTION_DLQ       = "prediction.dlq";

    private Topics() {}
}
