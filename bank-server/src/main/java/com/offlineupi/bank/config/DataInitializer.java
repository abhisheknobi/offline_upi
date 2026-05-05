package com.offlineupi.bank.config;

import com.offlineupi.bank.model.Account;
import com.offlineupi.bank.repository.AccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Seeds the database with test accounts on startup (only if empty).
 */
@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initAccounts(AccountRepository accountRepository) {
        return args -> {
            if (accountRepository.count() == 0) {
                System.out.println("[DataInitializer] Seeding test accounts...");

                accountRepository.save(new Account("alice@okbank", "Alice Sharma",
                        1_000_000L, sha256("1234")));   // ₹10,000
                accountRepository.save(new Account("bob@ybl", "Bob Patel",
                        500_000L, sha256("5678")));      // ₹5,000
                accountRepository.save(new Account("merchant@hdfc", "Quick Mart",
                        0L, sha256("9999")));            // ₹0
                accountRepository.save(new Account("charlie@sbi", "Charlie Kumar",
                        250_000L, sha256("4321")));      // ₹2,500

                System.out.println("[DataInitializer] ✓ 4 test accounts created.");
            } else {
                System.out.println("[DataInitializer] Accounts already exist — skipping seed.");
            }
        };
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 hashing failed", e);
        }
    }
}
