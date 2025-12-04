# Crypto Module Documentation

## Overview

This module implements symmetric encryption using **NaCl SecretBox** (XSalsa20-Poly1305), providing Authenticated Encryption (AEAD).

## Dependencies

### Gradle Dependencies

```groovy
dependencies {
    implementation 'com.goterl:lazysodium-android:5.1.0@aar'
    implementation 'net.java.dev.jna:jna:5.17.0@aar'
}
```

### Other Platforms

| Platform | Dependency |
|----------|------------|
| Java | `com.goterl:lazysodium-java:5.1.4` |
| Python | `pynacl` |
| Node.js | `tweetnacl` or `libsodium-wrappers` |
| Go | `golang.org/x/crypto/nacl/secretbox` |
| Rust | `sodiumoxide` or `crypto_secretbox` |

---

## Encryption Specification

| Parameter | Value | Description |
|-----------|-------|-------------|
| Algorithm | XSalsa20-Poly1305 | NaCl SecretBox |
| Key Length | 32 bytes (256 bits) | `SecretBox.KEYBYTES` |
| Nonce Length | 24 bytes (192 bits) | `SecretBox.NONCEBYTES` |
| MAC Length | 16 bytes (128 bits) | `SecretBox.MACBYTES` |
| Encoding | Base64 (DEFAULT) | Android Base64 |

---

## Data Format

### Encrypted Data Structure

```
+------------------+------------------------+
|      Nonce       |       Ciphertext       |
|    (24 bytes)    |   (plaintext + MAC)    |
+------------------+------------------------+
```

The final output is a Base64-encoded string.

### Ciphertext Length Calculation

```
Ciphertext Length = Nonce (24) + Plaintext Length + MAC (16)
Base64 Length ≈ ceil((24 + Plaintext Length + 16) / 3) * 4
```

---

## API Reference

### `encrypt(data: String, key: ByteArray): String`

Encrypts a string.

**Parameters:**
- `data` - The plaintext string to encrypt (UTF-8)
- `key` - A 32-byte encryption key

**Returns:**
- Base64-encoded encrypted data (includes nonce)

**Example:**
```kotlin
val key = Crypto.getKeyFromString("my-secret-password")
val encrypted = Crypto.encrypt("Hello, World!", key)
```

---

### `decrypt(encryptedData: String, key: ByteArray): String`

Decrypts encrypted data.

**Parameters:**
- `encryptedData` - Base64-encoded encrypted data
- `key` - A 32-byte key (must match the encryption key)

**Returns:**
- Decrypted plaintext string (UTF-8)

**Throws:**
- `IllegalArgumentException` - If data is invalid or decryption fails (wrong key/tampered data)

**Example:**
```kotlin
val key = Crypto.getKeyFromString("my-secret-password")
val decrypted = Crypto.decrypt(encryptedData, key)
```

---

### `getKeyFromString(keyString: String): ByteArray`

Derives a 32-byte key from a password string.

**Parameters:**
- `keyString` - User password or key string

**Returns:**
- A 32-byte key

**Derivation Method:**
```
1. SHA-256(keyString) → firstHash
2. SHA-256(firstHash + keyString) → key (32 bytes)
```

**Example:**
```kotlin
val key = Crypto.getKeyFromString("user-password-123")
// key is a 32-byte ByteArray
```

---

## Cross-Platform Implementation Reference

### Python (PyNaCl)

```python
import nacl.secret
import nacl.utils
import hashlib
import base64

def get_key_from_string(key_string: str) -> bytes:
    first_hash = hashlib.sha256(key_string.encode('utf-8')).digest()
    key = hashlib.sha256(first_hash + key_string.encode('utf-8')).digest()
    return key[:32]

def encrypt(data: str, key: bytes) -> str:
    box = nacl.secret.SecretBox(key)
    nonce = nacl.utils.random(nacl.secret.SecretBox.NONCE_SIZE)
    encrypted = box.encrypt(data.encode('utf-8'), nonce)
    # encrypted already contains nonce + ciphertext
    return base64.b64encode(encrypted).decode('utf-8')

def decrypt(encrypted_data: str, key: bytes) -> str:
    box = nacl.secret.SecretBox(key)
    encrypted = base64.b64decode(encrypted_data)
    decrypted = box.decrypt(encrypted)
    return decrypted.decode('utf-8')
```

### Node.js (tweetnacl)

```javascript
const nacl = require('tweetnacl');
const { createHash } = require('crypto');

function getKeyFromString(keyString) {
    const firstHash = createHash('sha256').update(keyString, 'utf8').digest();
    const key = createHash('sha256').update(Buffer.concat([firstHash, Buffer.from(keyString, 'utf8')])).digest();
    return key.slice(0, 32);
}

function encrypt(data, key) {
    const nonce = nacl.randomBytes(nacl.secretbox.nonceLength);
    const messageUint8 = new TextEncoder().encode(data);
    const ciphertext = nacl.secretbox(messageUint8, nonce, key);
    
    const result = new Uint8Array(nonce.length + ciphertext.length);
    result.set(nonce);
    result.set(ciphertext, nonce.length);
    
    return Buffer.from(result).toString('base64');
}

function decrypt(encryptedData, key) {
    const combined = Buffer.from(encryptedData, 'base64');
    const nonce = combined.slice(0, nacl.secretbox.nonceLength);
    const ciphertext = combined.slice(nacl.secretbox.nonceLength);
    
    const decrypted = nacl.secretbox.open(ciphertext, nonce, key);
    if (!decrypted) {
        throw new Error('Decryption failed');
    }
    
    return new TextDecoder().decode(decrypted);
}
```

### Go

```go
package crypto

import (
    "crypto/rand"
    "crypto/sha256"
    "encoding/base64"
    "errors"
    
    "golang.org/x/crypto/nacl/secretbox"
)

const (
    KeySize   = 32
    NonceSize = 24
)

func GetKeyFromString(keyString string) [KeySize]byte {
    firstHash := sha256.Sum256([]byte(keyString))
    combined := append(firstHash[:], []byte(keyString)...)
    key := sha256.Sum256(combined)
    return key
}

func Encrypt(data string, key [KeySize]byte) (string, error) {
    var nonce [NonceSize]byte
    if _, err := rand.Read(nonce[:]); err != nil {
        return "", err
    }
    
    encrypted := secretbox.Seal(nonce[:], []byte(data), &nonce, &key)
    return base64.StdEncoding.EncodeToString(encrypted), nil
}

func Decrypt(encryptedData string, key [KeySize]byte) (string, error) {
    combined, err := base64.StdEncoding.DecodeString(encryptedData)
    if err != nil {
        return "", err
    }
    
    if len(combined) < NonceSize {
        return "", errors.New("invalid encrypted data")
    }
    
    var nonce [NonceSize]byte
    copy(nonce[:], combined[:NonceSize])
    
    decrypted, ok := secretbox.Open(nil, combined[NonceSize:], &nonce, &key)
    if !ok {
        return "", errors.New("decryption failed")
    }
    
    return string(decrypted), nil
}
```

---

## Security Considerations

1. **Key Management**: Keys should be stored securely; avoid hardcoding in source code
2. **Nonce Uniqueness**: A random nonce is automatically generated for each encryption, ensuring security
3. **Authenticated Encryption**: SecretBox provides integrity verification; tampered data will fail to decrypt
4. **Base64 Encoding**: Android uses `Base64.DEFAULT`; ensure compatibility with other platforms

---

## Test Vectors

Use the following test data to verify cross-platform implementation compatibility:

```
Password: "test-password-123"
Plaintext: "Hello, World!"

Derived Key (hex): Calculated via double SHA-256 hash
```

It is recommended to use a known encrypted result for decryption testing to ensure cross-platform compatibility.
