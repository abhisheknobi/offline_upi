package com.offlineupi.bank.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offlineupi.bank.model.*;
import com.offlineupi.bank.repository.AccountRepository;
import com.offlineupi.bank.repository.TransactionRecordRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * TransactionService — The heart of the bank server.
 *
 * PROCESSING PIPELINE:
 *   [1] TTL Check → [2] Idempotency → [3] Decrypt AES Key →
 *   [4] Decrypt Payload → [5] Validate Accounts → [6] Verify PIN →
 *   [7] Check Balance → [8] Debit + Credit
 */
@Service
public class TransactionService {

    private final CryptoService cryptoService;
    private final AccountRepository accountRepository;
    private final TransactionRecordRepository transactionRecordRepository;
    private final ObjectMapper objectMapper;

    private static final long TTL_GRACE_PERIOD_MS = 30_000L;

    public TransactionService(CryptoService cryptoService,
                               AccountRepository accountRepository,
                               TransactionRecordRepository transactionRecordRepository,
                               ObjectMapper objectMapper) {
        this.cryptoService = cryptoService;
        this.accountRepository = accountRepository;
        this.transactionRecordRepository = transactionRecordRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TransactionResult processPacket(TransactionPacket packet) {
        String txId = packet.getTransactionId();
        System.out.println("\n[TransactionService] ─────────────────────────────────────");
        System.out.println("[TransactionService] Received packet: " + txId);
        System.out.println("[TransactionService] Hop count: " + packet.getHopCount());

        // ── STEP 1: TTL Validation ─────────────────────────────────────────
        long now = System.currentTimeMillis();
        long expiry = packet.getTtlExpiry();

        if (now > expiry + TTL_GRACE_PERIOD_MS) {
            System.out.println("[TransactionService] ✗ REJECTED: Packet TTL expired.");
            return TransactionResult.expired(txId);
        }
        System.out.println("[TransactionService] ✓ TTL valid (" +
                ((expiry - now) / 1000) + "s remaining)");

        // ── STEP 2: Idempotency Check ──────────────────────────────────────
        Optional<TransactionRecord> existingRecord = transactionRecordRepository.findById(txId);
        if (existingRecord.isPresent()) {
            System.out.println("[TransactionService] ✓ DUPLICATE detected — returning cached result.");
            return TransactionResult.duplicate(txId);
        }

        // Claim this transaction ID with a placeholder
        TransactionRecord record = new TransactionRecord();
        record.setTransactionId(txId);
        record.setStatus("PROCESSING");
        record.setHopCount(packet.getHopCount());
        record.setProcessedAt(LocalDateTime.now());

        try {
            transactionRecordRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException e) {
            // Another thread beat us — return DUPLICATE
            return TransactionResult.duplicate(txId);
        }

        System.out.println("[TransactionService] ✓ New transaction — proceeding...");

        // ── STEP 3 & 4: Decrypt ────────────────────────────────────────────
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
            updateRecord(record, result, null, null, null, null);
            return result;
        }

        // ── STEP 5: Validate Accounts (with pessimistic lock) ──────────────
        Optional<Account> senderOpt = accountRepository.findByUpiIdForUpdate(payload.getSenderUpiId());
        if (senderOpt.isEmpty()) {
            System.out.println("[TransactionService] ✗ Sender not found: " + payload.getSenderUpiId());
            TransactionResult result = TransactionResult.accountNotFound(txId, payload.getSenderUpiId());
            updateRecord(record, result, payload.getSenderUpiId(), payload.getReceiverUpiId(),
                    payload.getAmountInPaise(), payload.getNote());
            return result;
        }

        Optional<Account> receiverOpt = accountRepository.findByUpiIdForUpdate(payload.getReceiverUpiId());
        if (receiverOpt.isEmpty()) {
            System.out.println("[TransactionService] ✗ Receiver not found: " + payload.getReceiverUpiId());
            TransactionResult result = TransactionResult.accountNotFound(txId, payload.getReceiverUpiId());
            updateRecord(record, result, payload.getSenderUpiId(), payload.getReceiverUpiId(),
                    payload.getAmountInPaise(), payload.getNote());
            return result;
        }

        Account sender = senderOpt.get();
        Account receiver = receiverOpt.get();

        // ── STEP 6: Verify PIN ─────────────────────────────────────────────
        if (!sender.getPinHash().equals(payload.getPinHash())) {
            System.out.println("[TransactionService] ✗ PIN verification failed for: " + payload.getSenderUpiId());
            TransactionResult result = TransactionResult.invalidPin(txId);
            updateRecord(record, result, payload.getSenderUpiId(), payload.getReceiverUpiId(),
                    payload.getAmountInPaise(), payload.getNote());
            return result;
        }
        System.out.println("[TransactionService] ✓ PIN verified");

        // ── STEP 7 & 8: Debit Sender, Credit Receiver ─────────────────────
        long amount = payload.getAmountInPaise();
        System.out.println("[TransactionService] Transferring ₹" + (amount / 100.0) +
                " from " + sender.getUpiId() + " → " + receiver.getUpiId());

        if (sender.getBalanceInPaise() < amount) {
            System.out.println("[TransactionService] ✗ Insufficient funds. Balance: ₹" +
                    (sender.getBalanceInPaise() / 100.0));
            TransactionResult result = TransactionResult.insufficientFunds(txId);
            updateRecord(record, result, payload.getSenderUpiId(), payload.getReceiverUpiId(),
                    payload.getAmountInPaise(), payload.getNote());
            return result;
        }

        sender.setBalanceInPaise(sender.getBalanceInPaise() - amount);
        receiver.setBalanceInPaise(receiver.getBalanceInPaise() + amount);
        accountRepository.save(sender);
        accountRepository.save(receiver);

        String referenceNumber = "OUPI" + System.currentTimeMillis();
        TransactionResult result = TransactionResult.success(txId, sender.getBalanceInPaise(), referenceNumber);
        updateRecord(record, result, payload.getSenderUpiId(), payload.getReceiverUpiId(),
                payload.getAmountInPaise(), payload.getNote());

        System.out.println("[TransactionService] ✓ SUCCESS! Ref: " + referenceNumber);
        System.out.println("[TransactionService] Sender balance: ₹" + (sender.getBalanceInPaise() / 100.0));
        System.out.println("[TransactionService] Receiver balance: ₹" + (receiver.getBalanceInPaise() / 100.0));
        System.out.println("[TransactionService] ─────────────────────────────────────\n");

        return result;
    }

    private void updateRecord(TransactionRecord record, TransactionResult result,
                               String senderUpiId, String receiverUpiId, Long amountInPaise, String note) {
        record.setStatus(result.getStatus());
        record.setMessage(result.getMessage());
        record.setSenderBalanceInPaise(result.getSenderBalanceInPaise());
        record.setReferenceNumber(result.getReferenceNumber());
        record.setSenderUpiId(senderUpiId);
        record.setReceiverUpiId(receiverUpiId);
        record.setAmountInPaise(amountInPaise);
        record.setNote(note);
        transactionRecordRepository.save(record);
    }

    public Optional<TransactionResult> getStatus(String transactionId) {
        return transactionRecordRepository.findById(transactionId)
                .filter(r -> !"PROCESSING".equals(r.getStatus()))
                .map(r -> {
                    TransactionResult result = new TransactionResult();
                    result.setTransactionId(r.getTransactionId());
                    result.setStatus(r.getStatus());
                    result.setMessage(r.getMessage());
                    result.setSenderBalanceInPaise(r.getSenderBalanceInPaise());
                    result.setReferenceNumber(r.getReferenceNumber());
                    result.setProcessedAt(r.getProcessedAt());
                    return result;
                });
    }

    public long getProcessedCount() {
        return transactionRecordRepository.count();
    }

    public List<TransactionRecord> getAllTransactions() {
        return transactionRecordRepository.findAllByOrderByProcessedAtDesc();
    }
}
