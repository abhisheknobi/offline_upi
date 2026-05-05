# Offline UPI — Bluetooth Mesh Payment System (Full-Stack Implementation)

## Goal

Build a complete end-to-end **Offline UPI Bluetooth Mesh Payment System** with:
- **Backend**: Spring Boot 3 (Java 17) — Bank server with hybrid RSA+AES encryption, idempotency, TTL-based replay prevention
- **Frontend**: React (Vite) — Premium dashboard with mesh visualization, transaction management, and real-time status

The existing Java files (`CryptoService.java`, `TransactionService.java`, `MeshSimulator.java`) provide the core logic which will be integrated into a proper Spring Boot project structure.

---

## Proposed Changes

### 1. Backend — Spring Boot Bank Server (`bank-server/`)

#### [NEW] `bank-server/pom.xml`
Maven project config with Spring Boot 3, Java 17, Jackson, Spring Web dependencies.

#### [NEW] `bank-server/src/main/java/com/offlineupi/bank/BankServerApplication.java`
Spring Boot entry point.

#### [NEW] `bank-server/src/main/java/com/offlineupi/bank/config/CorsConfig.java`
CORS configuration allowing React frontend (localhost:5173) to call API.

---

#### Models

#### [NEW] `bank-server/src/main/java/com/offlineupi/bank/model/TransactionPacket.java`
Encrypted packet structure received from relay nodes:
- `transactionId` (UUID) — idempotency key
- `encryptedPayload` (Base64 AES-encrypted JSON)
- `encryptedAESKey` (Base64 RSA-encrypted AES key)
- `iv` (Base64 AES initialization vector)
- `ttlExpiry` (epoch ms)
- `hopCount`
- `senderDeviceId`

#### [NEW] `bank-server/src/main/java/com/offlineupi/bank/model/TransactionPayload.java`
Decrypted inner payload:
- `senderUpiId`, `receiverUpiId`, `amountInPaise`, `pinHash`, `currency`, `note`

#### [NEW] `bank-server/src/main/java/com/offlineupi/bank/model/TransactionResult.java`
Bank response with status enum (SUCCESS, DUPLICATE, EXPIRED, INSUFFICIENT_FUNDS, INVALID_PIN, ACCOUNT_NOT_FOUND, DECRYPTION_FAILED), message, reference number, balances, timestamp. Includes static factory methods for each status.

#### [NEW] `bank-server/src/main/java/com/offlineupi/bank/model/Account.java`
In-memory account model with UPI ID, owner name, balance (paise), PIN hash. Thread-safe `debit()` and `credit()` with `synchronized`.

---

#### Services

#### [NEW] `bank-server/src/main/java/com/offlineupi/bank/service/CryptoService.java`
Based on existing `CryptoService.java`. RSA-2048 key pair generation, AES key decryption, payload decryption.

#### [NEW] `bank-server/src/main/java/com/offlineupi/bank/service/TransactionService.java`
Based on existing `TransactionService.java`. Full processing pipeline: TTL → idempotency → decrypt → validate → PIN → debit/credit.

#### [NEW] `bank-server/src/main/java/com/offlineupi/bank/service/AccountRepository.java`
In-memory account store. Pre-loaded with 4 test accounts:
| UPI ID | Owner | Balance | PIN |
|---|---|---|---|
| `alice@okbank` | Alice Sharma | ₹10,000 | 1234 |
| `bob@ybl` | Bob Patel | ₹5,000 | 5678 |
| `merchant@hdfc` | Quick Mart | ₹0 | 9999 |
| `charlie@sbi` | Charlie Kumar | ₹2,500 | 4321 |

---

#### Controller

#### [NEW] `bank-server/src/main/java/com/offlineupi/bank/controller/TransactionController.java`
REST endpoints:
- `GET /api/keys/public` — Returns bank's RSA public key
- `POST /api/transaction/submit` — Process encrypted transaction packet
- `GET /api/transaction/{id}` — Poll transaction status
- `GET /api/health` — Health check
- `GET /api/admin/stats` — Admin stats (processed count, accounts)
- `GET /api/admin/accounts` — List all accounts with balances
- `GET /api/admin/transactions` — List all processed transactions

#### [NEW] `bank-server/src/main/resources/application.properties`
Server port 8080, application name.

---

### 2. Frontend — React Dashboard (`frontend/`)

#### Setup
- Vite + React (JavaScript)
- Vanilla CSS with premium dark theme
- Google Fonts (Inter)

#### Pages & Components

#### [NEW] Dashboard Page (`/`)
- **System Status Card**: Server health, uptime, processed transaction count
- **Account Balances**: Live grid showing all 4 accounts with animated balance displays
- **Recent Transactions**: Latest transactions with status badges
- **Mesh Network Visualization**: Animated SVG showing the mesh topology

#### [NEW] Send Transaction Page (`/send`)
- Premium form to create and send encrypted UPI transactions
- Auto-fetches bank's RSA public key
- Client-side AES-256 encryption + RSA key wrapping (using Web Crypto API)
- Configurable TTL, sender/receiver UPI selection, amount, PIN
- Real-time encryption status visualization

#### [NEW] Transaction History Page (`/history`)
- Searchable list of all processed transactions
- Status filters (SUCCESS, DUPLICATE, EXPIRED, etc.)
- Detailed view with decryption pipeline visualization

#### [NEW] Mesh Simulator Page (`/simulator`)
- Interactive visual simulation of the Bluetooth mesh gossip protocol
- Animated nodes (Alice → Eve → Bob → Stranger → Bank)
- Run the 5 scenarios from MeshSimulator.java visually
- Packet propagation animation with hop counter

#### Design System
- **Dark mode** with deep navy/charcoal base (`#0a0e1a`, `#111827`)
- **Accent colors**: Electric blue (`#3b82f6`), Emerald (`#10b981`), Amber warnings (`#f59e0b`)
- **Glassmorphism**: Cards with `backdrop-filter: blur(16px)`, semi-transparent backgrounds
- **Gradients**: Blue-to-purple hero sections, status-based gradients
- **Micro-animations**: Card hover lifts, number counters, pulse effects on live data
- **Typography**: Inter font, proper hierarchy

---

## User Review Required

> [!IMPORTANT]
> **Database**: The README specifies in-memory storage (ConcurrentHashMap/HashMap). Should I stick with this or use H2/PostgreSQL for persistence?

> [!IMPORTANT]
> **Client-Side Encryption**: The frontend "Send Transaction" page will implement real AES-256 + RSA encryption using the Web Crypto API. This means transactions sent from the React UI will be genuinely encrypted — the bank server will decrypt them. Is this the desired behavior?

> [!IMPORTANT]
> **Mesh Simulator**: The MeshSimulator.java is a Java console app. For the React frontend, I'll create a visual simulation page that demonstrates the same 5 scenarios with animated nodes and packet flow. The actual Java client-simulator module can also be built if you want to run it from the command line. Should I include both?

---

## Open Questions

1. **Port configuration**: Backend on `8080`, Frontend on `5173` (Vite default) — acceptable?
2. **Should the client-simulator Java module also be scaffolded**, or only the React frontend for simulation?

---

## Verification Plan

### Automated Tests
1. Start Spring Boot backend: `cd bank-server && mvn spring-boot:run`
2. Start React frontend: `cd frontend && npm run dev`
3. Test API endpoints via curl:
   - `curl http://localhost:8080/api/health`
   - `curl http://localhost:8080/api/keys/public`
   - `curl http://localhost:8080/api/admin/accounts`
4. Test end-to-end transaction flow from React UI
5. Verify all 5 scenarios work (normal, duplicate, expired, invalid PIN, insufficient funds)

### Manual Verification
- Visual inspection of the React dashboard for premium aesthetics
- Browser testing of the mesh simulator animation
- Verify encryption/decryption pipeline works end-to-end
