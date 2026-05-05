package com.offlineupi.bank.repository;

import com.offlineupi.bank.model.TransactionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRecordRepository extends JpaRepository<TransactionRecord, String> {

    List<TransactionRecord> findAllByOrderByProcessedAtDesc();

    List<TransactionRecord> findByStatus(String status);

    List<TransactionRecord> findBySenderUpiIdOrReceiverUpiId(String senderUpiId, String receiverUpiId);
}
