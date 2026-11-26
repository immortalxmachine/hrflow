package com.hrflow.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * One rejected CSV row and the reason it failed validation. Invalid rows are
 * recorded here rather than silently dropped.
 */
@Entity
@Table(name = "import_errors")
public class ImportError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "raw_row", nullable = false)
    private String rawRow;

    @Column(nullable = false)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ImportError() { }

    public ImportError(Long batchId, int rowNumber, String rawRow, String reason) {
        this.batchId = batchId;
        this.rowNumber = rowNumber;
        this.rawRow = rawRow;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public Long getBatchId() { return batchId; }
    public int getRowNumber() { return rowNumber; }
    public String getRawRow() { return rawRow; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
