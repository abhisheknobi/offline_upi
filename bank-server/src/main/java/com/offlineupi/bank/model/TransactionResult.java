package com.offlineupi.bank.model;

import java.time.LocalDateTime;

/**
 * DTO for the bank server's response to a transaction submission.
 * Contains static factory methods for each possible outcome.
 */
public class TransactionResult {

    private String transactionId;
    private String status;
    private String message;
    private Long senderBalanceInPaise;
    private String referenceNumber;
    private LocalDateTime processedAt;

    public TransactionResult() {}

    public TransactionResult(String transactionId, String status, String message,
                              Long senderBalanceInPaise, String referenceNumber) {
        this.transactionId = transactionId;
        this.status = status;
        this.message = message;
        this.senderBalanceInPaise = senderBalanceInPaise;
        this.referenceNumber = referenceNumber;
        this.processedAt = LocalDateTime.now();
    }

    // ── Static Factory Methods ──────────────────────────────────────────

    public static TransactionResult success(String txId, long senderBalance, String refNumber) {
        return new TransactionResult(txId, "SUCCESS",
                "Transaction processed successfully", senderBalance, refNumber);
    }

    public static TransactionResult duplicate(String txId) {
        return new TransactionResult(txId, "DUPLICATE",
                "Transaction already processed (idempotency)", null, null);
    }

    public static TransactionResult expired(String txId) {
        return new TransactionResult(txId, "EXPIRED",
                "Transaction TTL expired — possible replay attack blocked", null, null);
    }

    public static TransactionResult insufficientFunds(String txId) {
        return new TransactionResult(txId, "INSUFFICIENT_FUNDS",
                "Sender has insufficient balance", null, null);
    }

    public static TransactionResult invalidPin(String txId) {
        return new TransactionResult(txId, "INVALID_PIN",
                "PIN verification failed", null, null);
    }

    public static TransactionResult accountNotFound(String txId, String upiId) {
        return new TransactionResult(txId, "ACCOUNT_NOT_FOUND",
                "Account not found: " + upiId, null, null);
    }

    public static TransactionResult decryptionFailed(String txId) {
        return new TransactionResult(txId, "DECRYPTION_FAILED",
                "Failed to decrypt packet — possible tampering", null, null);
    }

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

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
}
