package com.offlineupi.bank.controller;

import com.offlineupi.bank.model.Account;
import com.offlineupi.bank.model.TransactionPacket;
import com.offlineupi.bank.model.TransactionRecord;
import com.offlineupi.bank.model.TransactionResult;
import com.offlineupi.bank.repository.AccountRepository;
import com.offlineupi.bank.service.CryptoService;
import com.offlineupi.bank.service.TransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class TransactionController {

    private final TransactionService transactionService;
    private final CryptoService cryptoService;
    private final AccountRepository accountRepository;
    private final Instant startTime = Instant.now();

    public TransactionController(TransactionService transactionService,
                                  CryptoService cryptoService,
                                  AccountRepository accountRepository) {
        this.transactionService = transactionService;
        this.cryptoService = cryptoService;
        this.accountRepository = accountRepository;
    }

    // ── Public Key Distribution ─────────────────────────────────────────

    @GetMapping("/keys/public")
    public ResponseEntity<Map<String, String>> getPublicKey() {
        Map<String, String> response = new HashMap<>();
        response.put("algorithm", "RSA-2048");
        response.put("format", "X.509");
        response.put("encoding", "Base64");
        response.put("publicKey", cryptoService.getPublicKeyBase64());
        return ResponseEntity.ok(response);
    }

    // ── Transaction Endpoints ───────────────────────────────────────────

    @PostMapping("/transaction/submit")
    public ResponseEntity<TransactionResult> submitTransaction(@RequestBody TransactionPacket packet) {
        TransactionResult result = transactionService.processPacket(packet);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/transaction/{id}")
    public ResponseEntity<?> getTransactionStatus(@PathVariable String id) {
        Optional<TransactionResult> result = transactionService.getStatus(id);
        if (result.isPresent()) {
            return ResponseEntity.ok(result.get());
        }
        Map<String, String> notFound = new HashMap<>();
        notFound.put("transactionId", id);
        notFound.put("status", "NOT_FOUND");
        notFound.put("message", "Transaction not found or still processing");
        return ResponseEntity.status(404).body(notFound);
    }

    // ── Health & Admin ──────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Offline UPI Bank Server");
        response.put("uptime", java.time.Duration.between(startTime, Instant.now()).toString());
        response.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        Map<String, Object> response = new HashMap<>();
        response.put("processedTransactions", transactionService.getProcessedCount());
        response.put("totalAccounts", accountRepository.count());
        response.put("uptime", java.time.Duration.between(startTime, Instant.now()).toString());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/accounts")
    public ResponseEntity<List<Map<String, Object>>> getAccounts() {
        List<Account> accounts = accountRepository.findAll();
        List<Map<String, Object>> result = accounts.stream().map(a -> {
            Map<String, Object> map = new HashMap<>();
            map.put("upiId", a.getUpiId());
            map.put("ownerName", a.getOwnerName());
            map.put("balanceInPaise", a.getBalanceInPaise());
            map.put("balanceFormatted", String.format("₹%.2f", a.getBalanceInPaise() / 100.0));
            return map;
        }).toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/admin/transactions")
    public ResponseEntity<List<TransactionRecord>> getTransactions() {
        return ResponseEntity.ok(transactionService.getAllTransactions());
    }
}
