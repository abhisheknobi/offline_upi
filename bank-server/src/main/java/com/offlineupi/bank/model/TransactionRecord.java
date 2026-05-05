package com.offlineupi.bank.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA Entity storing processed transaction records in PostgreSQL.
 * Used for idempotency (preventing double-spend) and transaction history.
 */
@Entity
@Table(name = "transaction_records")
public class TransactionRecord {

    @Id
    @Column(nullable = false, unique = true)
    private String transactionId;

    @Column(nullable = false)
    private String status;

    private String message;
    private Long senderBalanceInPaise;
    private String referenceNumber;
    private String senderUpiId;
    private String receiverUpiId;
    private Long amountInPaise;
    private Integer hopCount;
    private String note;

    @Column(nullable = false)
    private LocalDateTime processedAt;

    public TransactionRecord() {}

    // ── Getters and Setters ─────────────────────────────────────────────

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Long getSenderBalanceInPaise() { return senderBalanceInPaise; }
    public void setSenderBalanceInPaise(Long senderBalanceInPaise) { this.senderBalanceInPaise = senderBalanceInPaise; }

    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }

    public String getSenderUpiId() { return senderUpiId; }
    public void setSenderUpiId(String senderUpiId) { this.senderUpiId = senderUpiId; }

    public String getReceiverUpiId() { return receiverUpiId; }
    public void setReceiverUpiId(String receiverUpiId) { this.receiverUpiId = receiverUpiId; }

    public Long getAmountInPaise() { return amountInPaise; }
    public void setAmountInPaise(Long amountInPaise) { this.amountInPaise = amountInPaise; }

    public Integer getHopCount() { return hopCount; }
    public void setHopCount(Integer hopCount) { this.hopCount = hopCount; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
}
