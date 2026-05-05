# Offline UPI — Bluetooth Mesh Payment System

> **Pay even without internet.** A distributed relay system for UPI transactions using Bluetooth Mesh gossip protocol, hybrid RSA+AES encryption, idempotency, and TTL-based replay attack prevention.

---

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────────┐
│                        SYSTEM ARCHITECTURE                               │
│                                                                          │
│  ┌────────────┐   BT    ┌────────────┐   BT   ┌─────────────────────┐  │
│  │Alice's     │ ──────► │Stranger A  │ ──────► │Stranger B           │  │
│  │Phone       │         │(no net)    │         │(HAS internet) 🌐    │  │
│  │(no internet│         └────────────┘         └─────────┬───────────┘  │
│  │)           │                                          │ HTTPS        │
│  └────────────┘                                          ▼              │
│                                                  ┌───────────────┐      │
│                                                  │ Bank Server   │      │
│                                                  │ (Spring Boot) │      │
│                                                  │               │      │
│                                                  │ • RSA decrypt │      │
│                                                  │ • TTL check   │      │
│                                                  │ • Idempotency │      │
│                                                  │ • Debit/Credit│      │
│                                                  └───────────────┘      │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## Security Model

### Hybrid Encryption (AES-256 + RSA-2048)

```
SENDER SIDE (Offline Device):
═══════════════════════════════════════════════════════════════

  1. Generate random AES-256 key  →  K_aes

  2. Build payload JSON:
     {
       "senderUpiId": "alice@okbank",
       "receiverUpiId": "bob@ybl",
       "amountInPaise": 50000,
       "pinHash": "sha256(1234)",
       "currency": "INR",
       "note": "Coffee"
     }

  3. Encrypt payload with AES-CBC:
     encryptedPayload = AES_CBC_Encrypt(payloadJSON, K_aes, random_IV)

  4. Encrypt K_aes with Bank's RSA Public Key:
     encryptedAESKey = RSA_OAEP_Encrypt(K_aes, BankPublicKey)

  5. Build TransactionPacket:
     {
       "transactionId": "uuid-v4",       ← idempotency key
       "encryptedPayload": "...",         ← strangers can't read this
       "encryptedAESKey": "...",          ← only bank can decrypt
       "iv": "...",                       ← not secret, needed for AES
       "ttlExpiry": 1718000000000,        ← replay attack prevention
       "hopCount": 0
     }

RELAY NODE (Stranger with internet):
═══════════════════════════════════════════════════════════════
  • Receives packet via Bluetooth
  • Cannot read contents (encrypted)
  • Forwards raw packet to Bank Server via HTTPS

BANK SERVER (receives packet):
═══════════════════════════════════════════════════════════════
  1. Decrypt encryptedAESKey  →  K_aes   (using RSA Private Key)
  2. Decrypt encryptedPayload →  JSON    (using K_aes + IV)
  3. Verify PIN hash, check TTL, check idempotency
  4. Debit sender, credit receiver
```

### Why Hybrid Encryption?

| Concern | Solution |
|---|---|
| RSA is slow for large data | AES encrypts the data (fast, O(n)) |
| AES key distribution problem | RSA encrypts the AES key (secure) |
| Key agreement without pre-shared secret | Bank's RSA public key is distributed during app install |
| Padding oracle attacks | OAEP padding used for RSA |

---

## Idempotency — Double-Spend Prevention

```
PROBLEM: Alice's packet is received by 3 relay nodes simultaneously.
         All 3 forward to bank server. Without idempotency, Alice
         gets charged 3x.

SOLUTION: ConcurrentHashMap<String, TransactionResult> processedTxns

Algorithm: "Put If Absent"
  ┌─────────────────────────────────────────────────────────┐
  │  receive(packet):                                       │
  │    existing = processedTxns.putIfAbsent(txId, result)  │
  │    if existing != null:                                 │
  │        return DUPLICATE (don't process again)          │
  │    else:                                                │
  │        process transaction                              │
  │        processedTxns.put(txId, finalResult)            │
  └─────────────────────────────────────────────────────────┘

Result: First relay node wins. Others get DUPLICATE response.
        Alice is charged exactly ONCE.
```

---

## Replay Attack Prevention — TTL

```
ATTACK SCENARIO:
  • Malicious relay saves Alice's encrypted packet on Day 1
  • Day 2: Eve replays the packet to the bank server
  • Without TTL: Bank processes it again, Alice gets charged twice

TTL MECHANISM:
  • Sender sets: ttlExpiry = currentTime + 3 hours
  • Bank validates: if (currentTime > ttlExpiry + GRACE_PERIOD) → REJECT

  ──────────────────────────────────────────────────────────────────
  Time 0        Time 3h         Time 3h+30s      Time 1 Day
  │             │               │                │
  [CREATED]   [EXPIRES]    [GRACE EXPIRES]    [EVE REPLAYS]
     ↓            ↓               ↓                ↓
  BROADCAST   STILL OK      LAST CHANCE         REJECTED ✗
```

---

## Project Structure

```
offline-upi/
├── bank-server/                    Spring Boot Bank Backend
│   ├── pom.xml
│   └── src/main/java/com/offlineupi/bank/
│       ├── BankServerApplication.java      Entry point
│       ├── model/
│       │   ├── TransactionPacket.java      Encrypted packet structure
│       │   ├── TransactionPayload.java     Decrypted inner payload
│       │   ├── TransactionResult.java      Bank response model
│       │   └── Account.java               In-memory account model
│       ├── service/
│       │   ├── CryptoService.java         RSA keygen, AES/RSA decrypt
│       │   ├── TransactionService.java    Core logic: TTL, idempotency, debit/credit
│       │   └── AccountRepository.java     In-memory account store
│       └── controller/
│           └── TransactionController.java REST API endpoints
│
├── client-simulator/               Java Simulation Client
│   ├── pom.xml
│   └── src/main/java/com/offlineupi/client/
│       ├── MeshSimulator.java      End-to-end scenario runner
│       ├── MeshNode.java           Simulated Bluetooth mesh device
│       └── CryptoUtils.java       Client-side AES/RSA encryption
│
└── README.md
```

---

## API Reference

### `GET /api/keys/public`
Returns the bank's RSA-2048 public key. Called during app onboarding.

```json
{
  "algorithm": "RSA-2048",
  "format": "X.509",
  "encoding": "Base64",
  "publicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAO..."
}
```

### `POST /api/transaction/submit`
Relay node forwards encrypted packet to bank server.

**Request:**
```json
{
  "transactionId": "550e8400-e29b-41d4-a716-446655440000",
  "encryptedPayload": "Base64(AES_CBC(payloadJSON))",
  "encryptedAESKey": "Base64(RSA_OAEP(aesKey))",
  "iv": "Base64(randomIV)",
  "ttlExpiry": 1718123456789,
  "hopCount": 2,
  "senderDeviceId": "device-alice"
}
```

**Response (Success):**
```json
{
  "transactionId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "SUCCESS",
  "message": "Transaction processed successfully",
  "senderBalanceInPaise": 950000,
  "referenceNumber": "OUPI1718000000001",
  "processedAt": "2024-06-11T12:34:56Z"
}
```

**Possible Status Values:**
| Status | Meaning |
|---|---|
| `SUCCESS` | Transaction completed |
| `DUPLICATE` | Already processed (idempotency) |
| `EXPIRED` | TTL passed (replay attack blocked) |
| `INSUFFICIENT_FUNDS` | Sender's balance too low |
| `INVALID_PIN` | PIN hash mismatch |
| `ACCOUNT_NOT_FOUND` | Unknown UPI ID |
| `DECRYPTION_FAILED` | Packet may be tampered |

### `GET /api/transaction/{id}`
Poll for transaction status.

---

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+

### 1. Start the Bank Server
```bash
cd bank-server
mvn spring-boot:run
```

**Test accounts pre-loaded:**
| UPI ID | Owner | Balance | PIN |
|---|---|---|---|
| `alice@okbank` | Alice Sharma | ₹10,000 | 1234 |
| `bob@ybl` | Bob Patel | ₹5,000 | 5678 |
| `merchant@hdfc` | Quick Mart | ₹0 | 9999 |
| `charlie@sbi` | Charlie Kumar | ₹2,500 | 4321 |

### 2. Run the Simulator
```bash
cd client-simulator
mvn compile exec:java -Dexec.mainClass=com.offlineupi.client.MeshSimulator
```

This runs 5 scenarios:
1. ✅ Normal transaction (Alice → Bob ₹500)
2. ✅ Idempotency (same packet sent by 2 relay nodes)
3. ✅ Replay attack blocked (expired TTL)
4. ✅ Invalid PIN rejected
5. ✅ Insufficient funds blocked

### 3. Manual API Test (curl)
```bash
# Fetch public key
curl http://localhost:8080/api/keys/public

# Check health
curl http://localhost:8080/api/health

# Admin stats
curl http://localhost:8080/api/admin/stats
```

---

## Extension Ideas

| Feature | Implementation |
|---|---|
| Real BLE | Android: `BluetoothLeAdvertiser` + `BleScanner` |
| Persistent idempotency | Redis with TTL-based key expiry |
| Digital signatures | ECDSA signature on packet for sender authentication |
| Response routing | Return result packet back through mesh to sender |
| QR code receiver info | Encode receiverUPI in QR, scan offline |
| Hardware Security Module | Protect bank's RSA private key |
| Multi-bank federation | Each bank has own RSA key pair, routing by UPI suffix |

---

## Tech Stack

| Layer | Technology | Purpose |
|---|---|---|
| Backend | Java 17 + Spring Boot 3 | Bank server REST API |
| Cryptography | Java JCA/JCE | AES-256-CBC + RSA-2048-OAEP |
| Serialization | Jackson | JSON packet encoding |
| Mesh Simulation | Pure Java threads | Gossip protocol simulation |
| HTTP Client | Java 11 HttpClient | Relay → Bank HTTPS calls |
| Idempotency | ConcurrentHashMap | Thread-safe put-if-absent |
| Account Store | In-memory HashMap | Simulated bank database |
