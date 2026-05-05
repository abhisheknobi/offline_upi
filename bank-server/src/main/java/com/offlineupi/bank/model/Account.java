package com.offlineupi.bank.model;

import jakarta.persistence.*;

/**
 * JPA Entity representing a bank account.
 *
 * Thread-safety for balance operations is handled at the service layer
 * via pessimistic locking (SELECT ... FOR UPDATE).
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String upiId;

    @Column(nullable = false)
    private String ownerName;

    @Column(nullable = false)
    private long balanceInPaise;

    @Column(nullable = false)
    private String pinHash;

    @Version
    private Long version;

    public Account() {}

    public Account(String upiId, String ownerName, long balanceInPaise, String pinHash) {
        this.upiId = upiId;
        this.ownerName = ownerName;
        this.balanceInPaise = balanceInPaise;
        this.pinHash = pinHash;
    }

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUpiId() { return upiId; }
    public void setUpiId(String upiId) { this.upiId = upiId; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public long getBalanceInPaise() { return balanceInPaise; }
    public void setBalanceInPaise(long balanceInPaise) { this.balanceInPaise = balanceInPaise; }

    public String getPinHash() { return pinHash; }
    public void setPinHash(String pinHash) { this.pinHash = pinHash; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
