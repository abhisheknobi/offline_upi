package com.offlineupi.bank.model;

/**
 * DTO representing the decrypted inner payload of a transaction.
 * This is what's inside the AES-encrypted blob after decryption.
 */
public class TransactionPayload {

    private String senderUpiId;
    private String receiverUpiId;
    private long amountInPaise;
    private String pinHash;
    private String currency;
    private String note;

    public TransactionPayload() {}

    public String getSenderUpiId() { return senderUpiId; }
    public void setSenderUpiId(String senderUpiId) { this.senderUpiId = senderUpiId; }

    public String getReceiverUpiId() { return receiverUpiId; }
    public void setReceiverUpiId(String receiverUpiId) { this.receiverUpiId = receiverUpiId; }

    public long getAmountInPaise() { return amountInPaise; }
    public void setAmountInPaise(long amountInPaise) { this.amountInPaise = amountInPaise; }

    public String getPinHash() { return pinHash; }
    public void setPinHash(String pinHash) { this.pinHash = pinHash; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    @Override
    public String toString() {
        return String.format("Payload{%s → %s, ₹%.2f, note='%s'}",
                senderUpiId, receiverUpiId, amountInPaise / 100.0, note);
    }
}
