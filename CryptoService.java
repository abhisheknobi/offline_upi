package com.offlineupi.bank.service;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * CryptoService
 *
 * Handles all cryptographic operations for the Bank Server.
 *
 * ENCRYPTION SCHEME (Hybrid Encryption):
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  SENDER SIDE (Client App)                                       │
 * │                                                                 │
 * │  1. Generate random AES-256 key (K_aes)                        │
 * │  2. Encrypt payload with AES-CBC: encryptedPayload             │
 * │  3. Encrypt K_aes with Bank's RSA Public Key: encryptedAESKey  │
 * │  4. Bundle → TransactionPacket { encryptedPayload,             │
 * │               encryptedAESKey, iv, transactionId, ttl }        │
 * └─────────────────────────────────────────────────────────────────┘
 *          ↓ Bluetooth Mesh (strangers relay, can't read)
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  BANK SERVER SIDE (This Service)                                │
 * │                                                                 │
 * │  1. Decrypt encryptedAESKey with RSA Private Key → K_aes       │
 * │  2. Decrypt encryptedPayload with K_aes + IV → JSON payload    │
 * │  3. Verify PIN hash, validate TTL, check idempotency           │
 * │  4. Process transaction                                         │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * WHY HYBRID?
 * - RSA alone is too slow for large payloads
 * - AES alone requires sharing a symmetric key securely (impossible without RSA here)
 * - Together: AES encrypts data (fast), RSA encrypts the AES key (secure key exchange)
 */
@Service
public class CryptoService {

    private static final String RSA_ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String AES_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final int RSA_KEY_SIZE = 2048;

    /** The bank's RSA key pair — generated fresh on startup */
    private final KeyPair bankKeyPair;

    /**
     * On construction, generate a fresh RSA-2048 key pair.
     * In production, this key pair would be loaded from an HSM (Hardware Security Module)
     * and the private key would NEVER leave the HSM.
     */
    public CryptoService() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(RSA_KEY_SIZE, new SecureRandom());
        this.bankKeyPair = keyPairGenerator.generateKeyPair();
        System.out.println("[CryptoService] RSA-2048 key pair generated successfully.");
        System.out.println("[CryptoService] Public key (Base64): " +
                Base64.getEncoder().encodeToString(bankKeyPair.getPublic().getEncoded()).substring(0, 40) + "...");
    }

    /**
     * Returns the bank's RSA Public Key as a Base64-encoded string.
     * This is distributed to all UPI client apps so they can encrypt their packets.
     * In production, this would be signed by a certificate authority.
     */
    public String getPublicKeyBase64() {
        return Base64.getEncoder().encodeToString(bankKeyPair.getPublic().getEncoded());
    }

    /**
     * Returns the raw RSA PublicKey object.
     */
    public PublicKey getPublicKey() {
        return bankKeyPair.getPublic();
    }

    /**
     * Decrypts the AES key using the bank's RSA Private Key.
     *
     * @param encryptedAESKeyBase64 Base64-encoded RSA-encrypted AES key
     * @return The raw AES SecretKey
     * @throws Exception if decryption fails (tampered packet)
     */
    public SecretKey decryptAESKey(String encryptedAESKeyBase64) throws Exception {
        byte[] encryptedKeyBytes = Base64.getDecoder().decode(encryptedAESKeyBase64);

        Cipher rsaCipher = Cipher.getInstance(RSA_ALGORITHM);
        rsaCipher.init(Cipher.DECRYPT_MODE, bankKeyPair.getPrivate());
        byte[] aesKeyBytes = rsaCipher.doFinal(encryptedKeyBytes);

        return new SecretKeySpec(aesKeyBytes, "AES");
    }

    /**
     * Decrypts the transaction payload using AES-CBC.
     *
     * @param encryptedPayloadBase64 Base64-encoded AES-encrypted JSON
     * @param aesKey                 The decrypted AES SecretKey
     * @param ivBase64               Base64-encoded AES Initialization Vector
     * @return Plaintext JSON string of the transaction payload
     * @throws Exception if decryption fails
     */
    public String decryptPayload(String encryptedPayloadBase64, SecretKey aesKey, String ivBase64)
            throws Exception {
        byte[] encryptedPayloadBytes = Base64.getDecoder().decode(encryptedPayloadBase64);
        byte[] ivBytes = Base64.getDecoder().decode(ivBase64);

        Cipher aesCipher = Cipher.getInstance(AES_ALGORITHM);
        IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
        aesCipher.init(Cipher.DECRYPT_MODE, aesKey, ivSpec);

        byte[] decryptedBytes = aesCipher.doFinal(encryptedPayloadBytes);
        return new String(decryptedBytes);
    }

    /**
     * Reconstructs an RSA PublicKey from its Base64 representation.
     * Used for client-side simulation to verify key exchange.
     */
    public static PublicKey publicKeyFromBase64(String base64) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(spec);
    }
}
