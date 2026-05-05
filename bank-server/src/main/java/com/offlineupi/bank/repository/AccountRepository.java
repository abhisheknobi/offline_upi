package com.offlineupi.bank.repository;

import com.offlineupi.bank.model.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByUpiId(String upiId);

    /**
     * Pessimistic write lock — SELECT ... FOR UPDATE.
     * Ensures no concurrent modification during debit/credit operations.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.upiId = :upiId")
    Optional<Account> findByUpiIdForUpdate(@Param("upiId") String upiId);
}
