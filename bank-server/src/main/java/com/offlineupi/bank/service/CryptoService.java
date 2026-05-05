package com.offlineupi.bank.service;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * CryptoService — Handles all cryptographic operations for the Bank Server.
 *
 * ENCRYPTION SCHEME (Hybrid):
 *   SENDER:  AES-256-CBC encrypts payload → RSA-2048-OAEP encrypts AES key
 *   BANK:    RSA decrypts AES key → AES decrypts payload
 */
@Service
public class CryptoService {

    private static final String RSA_ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String AES_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final int RSA_KEY_SIZE = 2048;

    private final KeyPair bankKeyPair;

    public CryptoService() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(RSA_KEY_SIZE, new SecureRandom());
        this.bankKeyPair = keyPairGenerator.generateKeyPair();
        System.out.println("[CryptoService] RSA-2048 key pair generated successfully.");
        System.out.println("[CryptoService] Public key (Base64): " +
                Base64.getEncoder().encodeToString(bankKeyPair.getPublic().getEncoded()).substring(0, 40) + "...");
    }

    /** Returns the bank's RSA Public Key as a Base64-encoded string. */
    public String getPublicKeyBase64() {
        return Base64.getEncoder().encodeToString(bankKeyPair.getPublic().getEncoded());
    }

    /** Returns the raw RSA PublicKey object. */
    public PublicKey getPublicKey() {
        return bankKeyPair.getPublic();
    }

    /**
     * Decrypts the AES key using the bank's RSA Private Key.
     */
    public SecretKey decryptAESKey(String encryptedAESKeyBase64) throws Exception {
        byte[] encryptedKeyBytes = Base64.getDecoder().decode(encryptedAESKeyBase64);

        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        OAEPParameterSpec oaepParams = new OAEPParameterSpec(
                "SHA-256", "MGF1", new MGF1ParameterSpec("SHA-256"), PSource.PSpecified.DEFAULT);
        
        rsaCipher.init(Cipher.DECRYPT_MODE, bankKeyPair.getPrivate(), oaepParams);
        byte[] aesKeyBytes = rsaCipher.doFinal(encryptedKeyBytes);

        return new SecretKeySpec(aesKeyBytes, "AES");
    }

    /**
     * Decrypts the transaction payload using AES-CBC.
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

    /** Reconstructs an RSA PublicKey from its Base64 representation. */
    public static PublicKey publicKeyFromBase64(String base64) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(spec);
    }
}
