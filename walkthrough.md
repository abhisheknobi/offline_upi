# Offline UPI — Implementation Walkthrough

## What Was Built

A full-stack **Offline UPI Bluetooth Mesh Payment System** with:

### Backend — Spring Boot 3 + PostgreSQL
| Layer | Files |
|---|---|
| **Entry** | [BankServerApplication.java](file:///d:/SpringBoot%20Proj/upi/bank-server/src/main/java/com/offlineupi/bank/BankServerApplication.java) |
| **Config** | [CorsConfig.java](file:///d:/SpringBoot%20Proj/upi/bank-server/src/main/java/com/offlineupi/bank/config/CorsConfig.java), [DataInitializer.java](file:///d:/SpringBoot%20Proj/upi/bank-server/src/main/java/com/offlineupi/bank/config/DataInitializer.java) |
| **Models** | [Account.java](file:///d:/SpringBoot%20Proj/upi/bank-server/src/main/java/com/offlineupi/bank/model/Account.java), [TransactionPacket.java](file:///d:/SpringBoot%20Proj/upi/bank-server/src/main/java/com/offlineupi/bank/model/TransactionPacket.java), [TransactionPayload.java](file:///d:/SpringBoot%20Proj/upi/bank-server/src/main/java/com/offlineupi/bank/model/TransactionPayload.java), [TransactionResult.java](file:///d:/SpringBoot%20Proj/upi/bank-server/src/main/java/com/offlineupi/bank/model/TransactionResult.java), [TransactionRecord.java](file:///d:/SpringBoot%20Proj/upi/bank-server/src/main/java/com/offlineupi/bank/model/TransactionRecord.java) |
| **Repositories** | [AccountRepository.java](file:///d:/SpringBoot%20Proj/upi/bank-server/src/main/java/com/offlineupi/bank/repository/AccountRepository.java), [TransactionRecordRepository.java](file:///d:/SpringBoot%20Proj/upi/bank-server/src/main/java/com/offlineupi/bank/repository/TransactionRecordRepository.java) |
| **Services** | [CryptoService.java](file:///d:/SpringBoot%20Proj/upi/bank-server/src/main/java/com/offlineupi/bank/service/CryptoService.java), [TransactionService.java](file:///d:/SpringBoot%20Proj/upi/bank-server/src/main/java/com/offlineupi/bank/service/TransactionService.java) |
| **Controller** | [TransactionController.java](file:///d:/SpringBoot%20Proj/upi/bank-server/src/main/java/com/offlineupi/bank/controller/TransactionController.java) |

**Key Backend Features:**
- Hybrid RSA-2048 + AES-256-CBC encryption
- PostgreSQL persistence via Spring Data JPA
- Pessimistic locking for safe balance updates
- Idempotency via unique transaction ID in DB
- TTL-based replay attack prevention
- 4 pre-seeded test accounts

### Frontend — React + Vite + Tailwind CSS v4
| Page | File |
|---|---|
| **Dashboard** | [Dashboard.jsx](file:///d:/SpringBoot%20Proj/upi/frontend/src/pages/Dashboard.jsx) — Live stats, account balances, recent transactions |
| **Send Transaction** | [SendTransaction.jsx](file:///d:/SpringBoot%20Proj/upi/frontend/src/pages/SendTransaction.jsx) — Real client-side encryption with visual pipeline |
| **Transaction History** | [TransactionHistory.jsx](file:///d:/SpringBoot%20Proj/upi/frontend/src/pages/TransactionHistory.jsx) — Filterable history with status badges |
| **Mesh Simulator** | [MeshSimulator.jsx](file:///d:/SpringBoot%20Proj/upi/frontend/src/pages/MeshSimulator.jsx) — Interactive SVG mesh visualization with 4 scenarios |

**Key Frontend Features:**
- Real Web Crypto API encryption (AES-256-CBC + RSA-OAEP)
- Animated mesh network with SVG
- Premium dark theme with glassmorphism
- Auto-refreshing dashboard (5s interval)
- Responsive design

## How to Run

### Prerequisites
1. **Java 17+** and **Maven 3.8+**
2. **Node.js 18+** and **npm**
3. **PostgreSQL** running locally

### Step 1: Create PostgreSQL Database
```sql
CREATE DATABASE offline_upi;
```

### Step 2: Start Backend
```bash
cd bank-server
mvn spring-boot:run
```
Server starts on `http://localhost:8080`

### Step 3: Start Frontend
```bash
cd frontend
npm run dev
```
App opens at `http://localhost:5173`

### Step 4: Test
- Dashboard shows 4 accounts and system stats
- Use **Send** page to create encrypted transactions
- Use **Mesh Sim** to run all 4 scenarios visually
- View results in **History** page

## Validation
- ✅ Frontend builds successfully with Vite
- ⏳ Backend requires PostgreSQL database to be running
