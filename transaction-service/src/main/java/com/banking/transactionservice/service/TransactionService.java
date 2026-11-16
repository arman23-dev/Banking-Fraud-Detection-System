package com.banking.transactionservice.service;

import com.banking.transactionservice.client.AccountServiceClient;
import com.banking.transactionservice.dto.TransactionRequest;
import com.banking.transactionservice.dto.TransactionResponse;
import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.entity.TransactionStatus;
import com.banking.transactionservice.entity.TransactionType;
import com.banking.transactionservice.event.TransactionCompletedEvent;
import com.banking.transactionservice.event.TransactionInitiatedEvent;
import com.banking.transactionservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String ,String> redisTemplate;

    private static final String TRANSACTION_INITIATED_TOPIC = "transaction.initiated";
    private static final String TRANSACTION_COMPLETED_TOPIC = "transaction.completed";
    private static final String TRANSACTION_REFUNDED_TOPIC = "transaction.refunded";
    private static final String FRAUD_DETECTED_TOPIC = "fraud.detected";

    public TransactionResponse transfer(TransactionRequest transactionRequest) {
        log.info("SAGA START - Transfer: {} -> {} amount: {}", transactionRequest.getSenderAccountNumber(),
                transactionRequest.getRecieverAccountNumber(), transactionRequest.getAmount());

        accountServiceClient.deductBalance(
                transactionRequest.getSenderAccountNumber(),
                transactionRequest.getAmount());

        Transaction transaction = new Transaction();
        transaction.setSenderAccountNumber(transactionRequest.getSenderAccountNumber());
        transaction.setRecieverAccountNumber(transactionRequest.getRecieverAccountNumber());
        transaction.setAmount(transactionRequest.getAmount());
        transaction.setType(TransactionType.TRANSFER);
        transaction.setStatus(TransactionStatus.PROCESSING);
        transaction.setDescription(transactionRequest.getDescription());
        transaction.setReferenceNumber(UUID.randomUUID().toString());


        Transaction saveTransaction = transactionRepository.save(transaction);
        log.info("Transaction saved as PROCESSING: {}",saveTransaction.getId());

        TransactionInitiatedEvent event= new TransactionInitiatedEvent(
                saveTransaction.getId(),
                saveTransaction.getSenderAccountNumber(),
                saveTransaction.getRecieverAccountNumber(),
                saveTransaction.getAmount(),
                saveTransaction.getDescription()
        );

        kafkaTemplate.send(TRANSACTION_INITIATED_TOPIC, saveTransaction.getId(),event);
        log.info("SAGA STEP 2 - TransactionInitiatedEvent published: {}", saveTransaction.getId());

        return mapToResponse(saveTransaction);



    }

    private TransactionResponse mapToResponse(Transaction saveTransaction) {
        TransactionResponse response = new TransactionResponse();
        response.setId(saveTransaction.getId());
        response.setSenderAccountNumber(saveTransaction.getSenderAccountNumber());
        response.setRecieverAccountNumber(saveTransaction.getRecieverAccountNumber());
        response.setAmount(saveTransaction.getAmount());
        response.setType(saveTransaction.getType());
        response.setStatus(saveTransaction.getStatus());
        response.setReferenceNumber(saveTransaction.getReferenceNumber());
        response.setDescription(saveTransaction.getDescription());
        response.setFailureReason(saveTransaction.getFailureReason());
        response.setCompletedAt(saveTransaction.getCompletedAt());
        response.setCreatedAt(saveTransaction.getCreatedAt());


        return response;
    }

    public TransactionResponse getTransaction(String transactionId) {
        return mapToResponse(transactionRepository.findById(transactionId)
                .orElseThrow(()-> new RuntimeException("Transaction not found "+ transactionId)));
    }

    public List<TransactionResponse> getTransactionHistory(String accountNumber) {
        return transactionRepository
                .findBySenderAccountNumberOrderByCreatedAtDesc(accountNumber)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public TransactionResponse verifyOtp(String transactionId, String otp) {
        log.info("OTP verification for the transaction: {}", transactionId);

        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(()-> new RuntimeException("Transaction not found"));

        String otpkey= "verification:otp"+transactionId;
        String storeOtp = redisTemplate.opsForValue().get(otpkey);

        if(storeOtp ==null){
            log.warn("OTP expired for transsaction: {}" ,transactionId);
            compensateTransaction(transaction, "OTP expired - transaction cancelled and amount refunded");
            return mapToResponse(transaction);

        }
        if(!storeOtp.equals(otp)){
            log.warn("Wrong OTP - blocking accunt and refunding: {}", transactionId);
            redisTemplate.delete(otpkey);
            blockAccountAndCompensate(transaction,"Wrong Otp entered - transaction cancelled and account blocked for security");

            return mapToResponse(transaction);
        }
        log.info("OTP verified completing transaction: {}", transactionId);
        redisTemplate.delete(otpkey);
        completeTransaction(transaction);
        return mapToResponse(transaction);
    }



    private void compensateTransaction(Transaction transaction, String s) {
        log.warn("SAGA COMPENSATION - refunding: {} amount: {}", transaction.getSenderAccountNumber(),transaction.getAmount());

        accountServiceClient.creditBalance(
                transaction.getSenderAccountNumber(),
                transaction.getAmount()
        );
        transaction.setStatus(TransactionStatus.FAILED);
        transaction.setFailureReason(s+" -SAGA Compensation executed, amount refunded at "+ LocalDateTime.now());

        transactionRepository.save(transaction);

        Map<String ,Object> refundEvent = new HashMap<>();
        refundEvent.put("transactionId" , transaction.getId());
        refundEvent.put("senderAccountNumber", transaction.getSenderAccountNumber());
        refundEvent.put("amount" , transaction.getAmount());

        kafkaTemplate.send(TRANSACTION_REFUNDED_TOPIC,transaction.getId() , refundEvent);

        log.info("SAGA COMPENSATION COMPLETE - {} refunded to {} ",transaction.getAmount(),transaction.getSenderAccountNumber());

    }

    private void blockAccountAndCompensate(Transaction transaction, String s) {

        Map<String ,Object> fraudEvent = new HashMap<>();
        fraudEvent.put("transactionId", transaction.getId());
        fraudEvent.put("accountNumber", transaction.getSenderAccountNumber());
        fraudEvent.put("reason",s);

        kafkaTemplate.send(FRAUD_DETECTED_TOPIC,transaction.getSenderAccountNumber(),fraudEvent);

        log.warn("fraud.detected published - account: {} will be blocked, Kindly contact to the bank", transaction.getSenderAccountNumber());

        compensateTransaction(transaction,s);

    }

    private void completeTransaction(Transaction transaction) {

        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        TransactionCompletedEvent completedEvent = new TransactionCompletedEvent(
                transaction.getId(),
                transaction.getSenderAccountNumber(),
                transaction.getRecieverAccountNumber(),
                transaction.getAmount(),
                transaction.getDescription() );

        kafkaTemplate.send(TRANSACTION_COMPLETED_TOPIC,transaction.getId(),completedEvent);

        log.info("SAGA COMPLETE - Transaction {} complete", transaction.getId());

    }


    public void processCleanResult(String transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(()-> new RuntimeException("Transaction not found"));

        if (transaction.getStatus() != TransactionStatus.PROCESSING){
            log.info("Transaction not processing {} skipping", transactionId);
            return;
        }
        completeTransaction(transaction);
    }
}
