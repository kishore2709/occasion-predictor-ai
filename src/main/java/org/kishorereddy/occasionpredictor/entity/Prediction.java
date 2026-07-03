package org.kishorereddy.occasionpredictor.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.kishorereddy.occasionpredictor.model.OccasionType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "predictions")
@Getter
@Setter
@NoArgsConstructor
public class Prediction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String orderId;

    private String recipientName;
    private String recipientRelation;
    private String productName;
    private String productCategory;
    private String orderDate;

    @Column(columnDefinition = "TEXT")
    private String giftMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OccasionType predictedOccasion;

    @Column(nullable = false)
    private double confidenceScore;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> evidence;

    private String predictionSource;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prompt_version_id")
    private PromptVersion promptVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_version_id")
    private ModelVersion modelVersion;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
