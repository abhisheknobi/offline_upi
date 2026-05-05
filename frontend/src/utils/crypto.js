/**
 * Client-side crypto utilities using the Web Crypto API.
 * Implements the same hybrid encryption as the Java CryptoUtils:
 *   1. AES-256-CBC encrypts the payload
 *   2. RSA-OAEP encrypts the AES key
 */

/**
 * Import the bank's RSA public key from Base64 DER (X.509/SPKI format).
 */
export async function importRSAPublicKey(base64Key) {
  const binaryDer = Uint8Array.from(atob(base64Key), c => c.charCodeAt(0));
  return crypto.subtle.importKey(
    'spki',
    binaryDer.buffer,
    { name: 'RSA-OAEP', hash: 'SHA-256' },
    false,
    ['encrypt']
  );
}

/**
 * Generate a random AES-256 key.
 */
export async function generateAESKey() {
  return crypto.subtle.generateKey(
    { name: 'AES-CBC', length: 256 },
    true,
    ['encrypt']
  );
}

/**
 * Generate a random 16-byte IV for AES-CBC.
 */
export function generateIV() {
  return crypto.getRandomValues(new Uint8Array(16));
}

/**
 * Encrypt plaintext with AES-256-CBC.
 * Returns Base64-encoded ciphertext.
 */
export async function encryptAES(plaintext, aesKey, iv) {
  const encoder = new TextEncoder();
  const data = encoder.encode(plaintext);
  const encrypted = await crypto.subtle.encrypt(
    { name: 'AES-CBC', iv },
    aesKey,
    data
  );
  return arrayBufferToBase64(encrypted);
}

/**
 * Encrypt the raw AES key bytes with RSA-OAEP.
 * Returns Base64-encoded encrypted key.
 */
export async function encryptAESKeyWithRSA(aesKey, rsaPublicKey) {
  const rawKey = await crypto.subtle.exportKey('raw', aesKey);
  const encrypted = await crypto.subtle.encrypt(
    { name: 'RSA-OAEP' },
    rsaPublicKey,
    rawKey
  );
  return arrayBufferToBase64(encrypted);
}

/**
 * SHA-256 hash a string, return hex.
 */
export async function sha256(message) {
  const encoder = new TextEncoder();
  const data = encoder.encode(message);
  const hash = await crypto.subtle.digest('SHA-256', data);
  return Array.from(new Uint8Array(hash))
    .map(b => b.toString(16).padStart(2, '0'))
    .join('');
}

/**
 * Build a complete encrypted transaction packet.
 */
export async function buildTransactionPacket({
  senderUpiId, receiverUpiId, amountInPaise, pin, note, rsaPublicKey, ttlHours = 3
}) {
  // Step 1: Hash the PIN
  const pinHash = await sha256(pin);

  // Step 2: Build the inner payload
  const payload = JSON.stringify({
    senderUpiId,
    receiverUpiId,
    amountInPaise,
    pinHash,
    currency: 'INR',
    note: note || '',
  });

  // Step 3: Generate AES key and IV
  const aesKey = await generateAESKey();
  const iv = generateIV();

  // Step 4: Encrypt payload with AES
  const encryptedPayload = await encryptAES(payload, aesKey, iv);

  // Step 5: Encrypt AES key with RSA
  const encryptedAESKey = await encryptAESKeyWithRSA(aesKey, rsaPublicKey);

  // Step 6: Build packet
  const transactionId = crypto.randomUUID();
  const ttlExpiry = Date.now() + (ttlHours * 60 * 60 * 1000);

  return {
    transactionId,
    encryptedPayload,
    encryptedAESKey,
    iv: arrayBufferToBase64(iv.buffer),
    ttlExpiry,
    hopCount: 0,
    senderDeviceId: `device-${senderUpiId.split('@')[0]}`,
    createdAt: Date.now(),
  };
}

function arrayBufferToBase64(buffer) {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}
