package com.offlineupi.bank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BankServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankServerApplication.class, args);
        System.out.println("""
            ╔═══════════════════════════════════════════════════════╗
            ║       OFFLINE UPI BANK SERVER — STARTED               ║
            ║                                                       ║
            ║  Hybrid Encryption: AES-256-CBC + RSA-2048-OAEP      ║
            ║  Idempotency: PostgreSQL (unique transaction ID)      ║
            ║  Replay Prevention: TTL with grace period             ║
            ║  Database: PostgreSQL                                  ║
            ║                                                       ║
            ║  API: http://localhost:8080/api                        ║
            ╚═══════════════════════════════════════════════════════╝
            """);
    }
}
