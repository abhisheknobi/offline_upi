package com.offlineupi.bank.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offlineupi.bank.model.Account;
import com.offlineupi.bank.model.TransactionPacket;
import com.offlineupi.bank.model.TransactionPayload;
import com.offlineupi.bank.model.TransactionResult;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TransactionService
 *
 * The heart of the bank server. Processes incoming TransactionPackets from relay nodes.
 *
 * PROCESSING PIPELINE:
 * ┌──────────────────────────────────────────────────────────────┐
 * │  receive(packet)                                             │
 * │    │                                                         │
 * │    ├─[1]─ TTL Check ──────── expired? → REJECT              │
 * │    │                                                         │
 * │    ├─[2]─ Idempotency Check ─ seen? → return cached result  │
 * │    │                                                         │
 * │    ├─[3]─ Decrypt AES Key ──── RSA Private Key              │
 * │    │                                                         │
 * │    ├─[4]─ Decrypt Payload ──── AES + IV                     │
 * │    │                                                         │
 * │    ├─[5]─ Validate Accounts ── sender & receiver exist?     │
 * │    │                                                         │
 * │    ├─[6]─ Verify PIN ──────── SHA-256 hash comparison       │
 * │    │                                                         │
 * │    ├─[7]─ Check Balance ────── sufficient funds?            │
 * │    │                                                         │
 * │    └─[8]─ Debit + Credit ───── atomic, update balances      │
 * └──────────────────────────────────────────────────────────────┘
 */
@Service
public class TransactionService {

    private final CryptoService cryptoService;
    private final AccountRepository accountRepository;
    private final ObjectMapper objectMapper;

    /**
     * IDEMPOTENCY STORE
     *
     * A ConcurrentHashMap acting as the "processed transactions" ledger.
     * Key: transactionId (UUID)
     * Value: TransactionResult (the outcome when first processed)
     *
     * "Put If Absent" strategy:
     *   - First arrival: process it, store result, return result
     *   - Any subsequent arrival with same ID: return stored result immediately
     *
     * In production: Redis or DynamoDB with TTL-based expiry for horizontal scaling.
     * This in-memory store works for single-node simulation.
     */
    private final Map<String, TransactionResult> processedTransactions = new ConcurrentHashMap<>();

    /**
     * TTL_GRACE_PERIOD_MS
     *
     * Allow a small grace period (e.g., 30 seconds) for clock skew between
     * the sender's device and the bank server. This is common in distributed systems.
     */
    private static final long TTL_GRACE_PERIOD_MS = 30_000L;

    public TransactionService(CryptoService cryptoService,
                               AccountRepository accountRepository,
                               ObjectMapper objectMapper) {
        this.cryptoService = cryptoService;
        this.accountRepository = accountRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Main entry point — process an incoming TransactionPacket.
     *
     * @param packet The encrypted transaction packet from a relay node
     * @return TransactionResult indicating success or failure reason
     */
    public TransactionResult processPacket(TransactionPacket packet) {
        String txId = packet.getTransactionId();
        System.out.println("\n[TransactionService] ─────────────────────────────────────");
        System.out.println("[TransactionService] Received packet: " + txId);
        System.out.println("[TransactionService] Hop count: " + packet.getHopCount());

        // ── STEP 1: TTL Validation (Replay Attack Prevention) ──────────────────
        long now = System.currentTimeMillis();
        long expiry = packet.getTtlExpiry();
        System.out.println("[TransactionService] TTL check: now=" + now + ", expiry=" + expiry);

        if (now > expiry + TTL_GRACE_PERIOD_MS) {
            System.out.println("[TransactionService] ✗ REJECTED: Packet TTL expired.");
            TransactionResult result = TransactionResult.expired(txId);
            // Don't store expired packets in idempotency map — let them all be rejected
            return result;
        }
        System.out.println("[TransactionService] ✓ TTL valid (" +
                ((expiry - now) / 1000) + "s remaining)");

        // ── STEP 2: Idempotency Check ──────────────────────────────────────────
        // putIfAbsent returns null if the key was NOT already present (first time)
        // Returns the existing value if the key WAS already present (duplicate)
        TransactionResult existingResult = processedTransactions.putIfAbsent(txId, new TransactionResult());

        if (existingResult != null) {
            System.out.println("[TransactionService] ✓ DUPLICATE detected — returning cached result.");
            return TransactionResult.duplicate(txId);
        }

        // We are now the "first" to process this transaction
        System.out.println("[TransactionService] ✓ New transaction — proceeding...");

        // ── STEP 3 & 4: Decrypt the packet ────────────────────────────────────
        TransactionPayload payload;
        try {
            System.out.println("[TransactionService] Decrypting AES key using RSA private key...");
            SecretKey aesKey = cryptoService.decryptAESKey(packet.getEncryptedAESKey());

            System.out.println("[TransactionService] Decrypting payload using AES-256-CBC...");
            String payloadJson = cryptoService.decryptPayload(
                    packet.getEncryptedPayload(), aesKey, packet.getIv());

            payload = objectMapper.readValue(payloadJson, TransactionPayload.class);
            System.out.println("[TransactionService] ✓ Decryption successful: " + payload);
        } catch (Exception e) {
            System.out.println("[TransactionService] ✗ DECRYPTION FAILED: " + e.getMessage());
            TransactionResult result = TransactionResult.decryptionFailed(txId);
            processedTransactions.put(txId, result); // Mark as processed (with failure)
            return result;
        }

        // ── STEP 5: Validate Accounts ──────────────────────────────────────────
        Optional<Account> senderOpt = accountRepository.findByUpiId(payload.getSenderUpiId());
        if (senderOpt.isEmpty()) {
            System.out.println("[TransactionService] ✗ Sender not found: " + payload.getSenderUpiId());
            TransactionResult result = TransactionResult.accountNotFound(txId, payload.getSenderUpiId());
            processedTransactions.put(txId, result);
            return result;
        }

        Optional<Account> receiverOpt = accountRepository.findByUpiId(payload.getReceiverUpiId());
        if (receiverOpt.isEmpty()) {
            System.out.println("[TransactionService] ✗ Receiver not found: " + payload.getReceiverUpiId());
            TransactionResult result = TransactionResult.accountNotFound(txId, payload.getReceiverUpiId());
            processedTransactions.put(txId, result);
            return result;
        }

        Account sender = senderOpt.get();
        Account receiver = receiverOpt.get();

        // ── STEP 6: Verify PIN ─────────────────────────────────────────────────
        if (!sender.getPinHash().equals(payload.getPinHash())) {
            System.out.println("[TransactionService] ✗ PIN verification failed for: " + payload.getSenderUpiId());
            TransactionResult result = TransactionResult.invalidPin(txId);
            processedTransactions.put(txId, result);
            return result;
        }
        System.out.println("[TransactionService] ✓ PIN verified");

        // ── STEP 7 & 8: Debit Sender, Credit Receiver ─────────────────────────
        long amount = payload.getAmountInPaise();
        System.out.println("[TransactionService] Transferring ₹" + (amount / 100.0) +
                " from " + sender.getUpiId() + " → " + receiver.getUpiId());

        // Thread-safe debit
        boolean debited = sender.debit(amount);
        if (!debited) {
            System.out.println("[TransactionService] ✗ Insufficient funds. Balance: ₹" +
                    (sender.getBalanceInPaise() / 100.0));
            TransactionResult result = TransactionResult.insufficientFunds(txId);
            processedTransactions.put(txId, result);
            return result;
        }

        // Thread-safe credit
        receiver.credit(amount);

        String referenceNumber = "OUPI" + System.currentTimeMillis();
        TransactionResult result = TransactionResult.success(txId, sender.getBalanceInPaise(), referenceNumber);
        processedTransactions.put(txId, result);

        System.out.println("[TransactionService] ✓ SUCCESS! Ref: " + referenceNumber);
        System.out.println("[TransactionService] Sender balance: ₹" + (sender.getBalanceInPaise() / 100.0));
        System.out.println("[TransactionService] Receiver balance: ₹" + (receiver.getBalanceInPaise() / 100.0));
        System.out.println("[TransactionService] ─────────────────────────────────────\n");

        return result;
    }

    /**
     * Check the status of a previously submitted transaction.
     * Used by relay nodes / clients to poll for result.
     */
    public Optional<TransactionResult> getStatus(String transactionId) {
        TransactionResult result = processedTransactions.get(transactionId);
        // If the placeholder was put but not yet replaced with a real result, return empty
        if (result == null || result.getStatus() == null) {
            return Optional.empty();
        }
        return Optional.of(result);
    }

    /** For the admin dashboard */
    public int getProcessedCount() {
        return processedTransactions.size();
    }
}
