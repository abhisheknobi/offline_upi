package com.offlineupi.bank.model;

/**
 * DTO representing the encrypted transaction packet received from relay nodes.
 *
 * Structure:
 * {
 *   "transactionId": "uuid-v4",          ← idempotency key
 *   "encryptedPayload": "Base64(...)",   ← AES-CBC encrypted JSON
 *   "encryptedAESKey": "Base64(...)",    ← RSA-OAEP encrypted AES key
 *   "iv": "Base64(...)",                 ← AES initialization vector
 *   "ttlExpiry": 1718000000000,          ← replay attack prevention
 *   "hopCount": 2,                       ← mesh hops taken
 *   "senderDeviceId": "device-alice"
 * }
 */
public class TransactionPacket {

    private String transactionId;
    private String encryptedPayload;
    private String encryptedAESKey;
    private String iv;
    private long ttlExpiry;
    private int hopCount;
    private String senderDeviceId;

    public TransactionPacket() {}

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getEncryptedPayload() { return encryptedPayload; }
    public void setEncryptedPayload(String encryptedPayload) { this.encryptedPayload = encryptedPayload; }

    public String getEncryptedAESKey() { return encryptedAESKey; }
    public void setEncryptedAESKey(String encryptedAESKey) { this.encryptedAESKey = encryptedAESKey; }

    public String getIv() { return iv; }
    public void setIv(String iv) { this.iv = iv; }

    public long getTtlExpiry() { return ttlExpiry; }
    public void setTtlExpiry(long ttlExpiry) { this.ttlExpiry = ttlExpiry; }

    public int getHopCount() { return hopCount; }
    public void setHopCount(int hopCount) { this.hopCount = hopCount; }

    public String getSenderDeviceId() { return senderDeviceId; }
    public void setSenderDeviceId(String senderDeviceId) { this.senderDeviceId = senderDeviceId; }
}
