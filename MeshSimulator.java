package com.offlineupi.client;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.SecretKey;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.PublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MeshSimulator
 *
 * Runs a complete end-to-end simulation of the Offline UPI Mesh system.
 *
 * WHAT THIS SIMULATES:
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │                      NETWORK TOPOLOGY                               │
 * │                                                                     │
 * │   [Alice]──BT──[Eve(no net)]──BT──[Stranger(HAS net)]──HTTPS──    │
 * │   (Sender)      (no internet)       (Relay Bridge)          │      │
 * │   (no net)                                                   ▼      │
 * │                                                        [Bank Server]│
 * └─────────────────────────────────────────────────────────────────────┘
 *
 * SCENARIOS DEMONSTRATED:
 *  1. Normal transaction flow (Alice → Bob, 2 hops)
 *  2. Duplicate packet (same ID sent twice — idempotency test)
 *  3. Expired TTL (replay attack simulation)
 *  4. Insufficient funds
 *  5. Invalid PIN
 *
 * HOW TO RUN:
 *  1. Start the bank server: cd bank-server && mvn spring-boot:run
 *  2. Run this: cd client-simulator && mvn compile exec:java -Dexec.mainClass=com.offlineupi.client.MeshSimulator
 */
public class MeshSimulator {

    private static final String BANK_SERVER_URL = "http://localhost:8080/api";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public static void main(String[] args) throws Exception {
        printBanner();

        // ── Fetch Bank's RSA Public Key ────────────────────────────────────────
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("STEP 1: Fetching Bank's RSA Public Key");
        System.out.println("═══════════════════════════════════════════════════");

        String publicKeyBase64 = fetchBankPublicKey();
        PublicKey bankPublicKey = CryptoUtils.rsaPublicKeyFromBase64(publicKeyBase64);
        System.out.println("✓ RSA Public Key fetched: " +
                publicKeyBase64.substring(0, 40) + "...\n");

        // ── Scenario 1: Normal Transaction ─────────────────────────────────────
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("SCENARIO 1: Normal Transaction (Alice → Bob, ₹500)");
        System.out.println("═══════════════════════════════════════════════════");
        runScenario(bankPublicKey,
                "alice@okbank", "bob@ybl", 50_000L, "1234",
                "Coffee payment", 3 * 60 * 60 * 1000L, null);

        Thread.sleep(1000);

        // ── Scenario 2: Idempotency (same transaction twice) ───────────────────
        System.out.println("\n═══════════════════════════════════════════════════");
        System.out.println("SCENARIO 2: Idempotency — Same Packet Sent Twice");
        System.out.println("(Simulates two relay nodes forwarding the same packet)");
        System.out.println("═══════════════════════════════════════════════════");
        String fixedId = UUID.randomUUID().toString();
        System.out.println("→ First relay sends the packet...");
        runScenarioWithId(bankPublicKey, fixedId,
                "alice@okbank", "bob@ybl", 10_000L, "1234",
                "Duplicate test", 3 * 60 * 60 * 1000L);

        System.out.println("\n→ Second relay sends the SAME packet (different relay node)...");
        runScenarioWithId(bankPublicKey, fixedId,
                "alice@okbank", "bob@ybl", 10_000L, "1234",
                "Duplicate test", 3 * 60 * 60 * 1000L);

        Thread.sleep(1000);

        // ── Scenario 3: Expired TTL ────────────────────────────────────────────
        System.out.println("\n═══════════════════════════════════════════════════");
        System.out.println("SCENARIO 3: Replay Attack — Expired TTL");
        System.out.println("(Packet with TTL set in the past)");
        System.out.println("═══════════════════════════════════════════════════");
        runScenario(bankPublicKey,
                "alice@okbank", "bob@ybl", 20_000L, "1234",
                "Old payment", -60_000L, null); // negative = already expired

        Thread.sleep(1000);

        // ── Scenario 4: Invalid PIN ────────────────────────────────────────────
        System.out.println("\n═══════════════════════════════════════════════════");
        System.out.println("SCENARIO 4: Invalid PIN");
        System.out.println("═══════════════════════════════════════════════════");
        runScenario(bankPublicKey,
                "alice@okbank", "merchant@hdfc", 15_000L, "0000", // wrong PIN
                "Groceries", 3 * 60 * 60 * 1000L, null);

        Thread.sleep(1000);

        // ── Scenario 5: Insufficient Funds ────────────────────────────────────
        System.out.println("\n═══════════════════════════════════════════════════");
        System.out.println("SCENARIO 5: Insufficient Funds");
        System.out.println("═══════════════════════════════════════════════════");
        runScenario(bankPublicKey,
                "charlie@sbi", "merchant@hdfc", 100_000_000L, "4321", // ₹10 lakh
                "Big purchase", 3 * 60 * 60 * 1000L, null);

        System.out.println("\n✓ All scenarios complete!");
    }

    // ── Core Transaction Flow ─────────────────────────────────────────────────

    private static void runScenario(PublicKey bankPublicKey,
                                     String senderUpi, String receiverUpi,
                                     long amountPaise, String pin, String note,
                                     long ttlOffsetMs, String overrideId) throws Exception {
        String txId = (overrideId != null) ? overrideId : UUID.randomUUID().toString();
        runScenarioWithId(bankPublicKey, txId, senderUpi, receiverUpi, amountPaise, pin, note, ttlOffsetMs);
    }

    private static void runScenarioWithId(PublicKey bankPublicKey, String txId,
                                           String senderUpi, String receiverUpi,
                                           long amountPaise, String pin, String note,
                                           long ttlOffsetMs) throws Exception {
        System.out.println("\n[Sender Device] Building transaction packet...");
        System.out.printf("[Sender Device] %s → %s  |  ₹%.2f  |  Note: %s%n",
                senderUpi, receiverUpi, amountPaise / 100.0, note);
        System.out.println("[Sender Device] Transaction ID: " + txId);

        // Step A: Build inner payload JSON
        String pinHash = CryptoUtils.sha256(pin);
        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("senderUpiId", senderUpi);
        payloadMap.put("receiverUpiId", receiverUpi);
        payloadMap.put("amountInPaise", amountPaise);
        payloadMap.put("pinHash", pinHash);
        payloadMap.put("currency", "INR");
        payloadMap.put("note", note);
        String payloadJson = MAPPER.writeValueAsString(payloadMap);
        System.out.println("[Sender Device] Inner payload: " + payloadJson);

        // Step B: Encrypt with AES-256
        SecretKey aesKey = CryptoUtils.generateAESKey();
        byte[] iv = CryptoUtils.generateIV();
        String encryptedPayload = CryptoUtils.encryptAES(payloadJson.getBytes(), aesKey, iv);
        System.out.println("[Sender Device] AES-256 encrypted payload: " +
                encryptedPayload.substring(0, Math.min(40, encryptedPayload.length())) + "...");

        // Step C: Encrypt AES key with RSA public key
        String encryptedAESKey = CryptoUtils.encryptAESKeyWithRSA(aesKey, bankPublicKey);
        System.out.println("[Sender Device] RSA-encrypted AES key: " +
                encryptedAESKey.substring(0, Math.min(40, encryptedAESKey.length())) + "...");

        // Step D: Build the packet
        long ttlExpiry = System.currentTimeMillis() + ttlOffsetMs;
        Map<String, Object> packet = new HashMap<>();
        packet.put("transactionId", txId);
        packet.put("encryptedPayload", encryptedPayload);
        packet.put("encryptedAESKey", encryptedAESKey);
        packet.put("iv", Base64.getEncoder().encodeToString(iv));
        packet.put("ttlExpiry", ttlExpiry);
        packet.put("hopCount", 0);
        packet.put("senderDeviceId", "device-" + senderUpi.split("@")[0]);
        packet.put("createdAt", System.currentTimeMillis());

        String packetJson = MAPPER.writeValueAsString(packet);
        System.out.println("\n[Sender Device] 📡 Broadcasting packet over Bluetooth...");

        // Step E: Simulate mesh — gossip through nodes
        simulateMeshGossip(packetJson, txId, packet);
    }

    private static void simulateMeshGossip(String packetJson, String txId,
                                            Map<String, Object> packet) throws Exception {
        System.out.println("\n── BLUETOOTH MESH SIMULATION ──────────────────────");

        // Build the mesh topology:
        // [Sender] → [Eve (no net)] → [Bob's phone (no net)] → [Stranger (HAS internet)]
        MeshNode sender = new MeshNode("n0", "Sender (Alice)", false, 5);
        MeshNode eve = new MeshNode("n1", "Eve (observer, no net)", false, 5);
        MeshNode bob = new MeshNode("n2", "Bob (receiver, no net)", false, 5);
        MeshNode stranger = new MeshNode("n3", "Stranger (has internet)", true, 5);

        // Topology: Alice ─BT─ Eve ─BT─ Bob ─BT─ Stranger
        sender.addNeighbor(eve);
        eve.addNeighbor(bob);
        bob.addNeighbor(stranger);

        // When the relay node (stranger) receives the packet, it forwards to bank server
        stranger.setRelayListener((relayer, transactionId, hasInternet) -> {
            System.out.println("\n  [" + relayer.getOwnerName() + "] Forwarding to bank over HTTPS...");
            try {
                Map<?, ?> result = forwardToBankServer(packetJson);
                System.out.println("\n── BANK SERVER RESPONSE ────────────────────────────");
                System.out.println("  Status: " + result.get("status"));
                System.out.println("  Message: " + result.get("message"));
                if (result.get("referenceNumber") != null) {
                    System.out.println("  Reference: " + result.get("referenceNumber"));
                }
                if (result.get("senderBalanceInPaise") != null) {
                    double balanceRs = ((Number) result.get("senderBalanceInPaise")).doubleValue() / 100.0;
                    System.out.printf("  Sender's new balance: ₹%.2f%n", balanceRs);
                }
                System.out.println("────────────────────────────────────────────────────");
            } catch (Exception e) {
                System.out.println("  ✗ Failed to reach bank server: " + e.getMessage());
                System.out.println("  (Make sure bank server is running on localhost:8080)");
            }
        });

        // Alice broadcasts the packet — mesh gossip begins
        System.out.println("  [Alice] 📡 Broadcasting via Bluetooth...");
        for (MeshNode neighbor : new MeshNode[]{ eve }) {
            neighbor.receivePacket(packetJson, txId, 1);
        }
    }

    // ── HTTP Communication ────────────────────────────────────────────────────

    private static String fetchBankPublicKey() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BANK_SERVER_URL + "/keys/public"))
                .GET()
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to fetch public key. Is bank server running?");
        }

        Map<?, ?> body = MAPPER.readValue(response.body(), Map.class);
        return (String) body.get("publicKey");
    }

    private static Map<?, ?> forwardToBankServer(String packetJson) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BANK_SERVER_URL + "/transaction/submit"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(packetJson))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request,
                HttpResponse.BodyHandlers.ofString());

        return MAPPER.readValue(response.body(), Map.class);
    }

    private static void printBanner() {
        System.out.println("""
            ╔═══════════════════════════════════════════════════════╗
            ║           OFFLINE UPI MESH SIMULATOR                  ║
            ║                                                       ║
            ║  Hybrid Encryption: AES-256 + RSA-2048               ║
            ║  Idempotency: ConcurrentHashMap (Put-if-Absent)       ║
            ║  Replay Prevention: Time-To-Live (TTL)                ║
            ║  Transport: Bluetooth Mesh (Gossip Protocol)          ║
            ╚═══════════════════════════════════════════════════════╝
            """);
    }
}
