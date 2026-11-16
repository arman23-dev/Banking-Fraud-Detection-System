package com.banking.accountservice.service;

import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.entity.Account;
import com.banking.accountservice.entity.AccountStatus;
import com.banking.accountservice.entity.AccountType;
import com.banking.accountservice.repository.AccountRepository;
import jakarta.validation.Valid;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;

@Service
@Slf4j
@RequiredArgsConstructor

public class AccountService {

    private final AccountRepository accountRepository;
    private static SecureRandom secureRandom= new SecureRandom();


    public AccountResponse createAccount( CreateAccountRequest createAccountRequest) {
        log.info("Creating account for: {}", createAccountRequest.getEmail());

        if(accountRepository.existsByEmail(createAccountRequest.getEmail())){
            throw new RuntimeException("Account already exits for  email: " + createAccountRequest.getEmail());
        }

        Account account = new Account();
        account.setAccountHolderName(createAccountRequest.getAccountHolderName());
        account.setEmail(createAccountRequest.getEmail());
        account.setPhone(createAccountRequest.getPhone());
        account.setAccountType(createAccountRequest.getAccountType());
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setBalance(createAccountRequest.getInitialDeposit());
        account.setAccountNumber(generateAccountNumber());
        account.setDailyTransactionLimit(
                createAccountRequest.getAccountType() == AccountType.SAVINGS
                        ? new BigDecimal("100000")
                        : new BigDecimal("500000")
        );

        Account savedAccount = accountRepository.save(account);
        log.info("Account created: {}", savedAccount.getAccountNumber());
        return mapToResponse(savedAccount);
    }

    private String generateAccountNumber() {

        String accountNumber;

        do{
            long number = secureRandom.nextLong(1_000_000_000_000L);
            accountNumber = String.format("%012d", number);
        }while (accountRepository.existsByAccountNumber(accountNumber));

        return accountNumber;

    }

    private AccountResponse mapToResponse(Account account) {

        AccountResponse accountResponse = new AccountResponse();
        accountResponse.setId(account.getId());
        accountResponse.setAccountNumber(account.getAccountNumber());
        accountResponse.setAccountHolderName(account.getAccountHolderName());
        accountResponse.setEmail(account.getEmail());
        accountResponse.setPhone(account.getPhone());
        accountResponse.setAccountStatus(account.getAccountStatus());
        accountResponse.setAccountType(account.getAccountType());
        accountResponse.setBalance(account.getBalance());
        accountResponse.setDailyTransactionLimit(accountResponse.getDailyTransactionLimit());
        accountResponse.setCreatedAt(account.getCreatedAt());

        return accountResponse;
    }

    public AccountResponse getAccount(String accountNumber) {

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(()-> new RuntimeException("Account Not found"));

        return mapToResponse(account);
    }

    public BigDecimal getBalance(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(()-> new RuntimeException("Account Not found"));

        return account.getBalance();
    }

    public void blockAccount(String accountNumber) {
        log.info("Blocking Account {}" , accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(()-> new RuntimeException("Account Not found"));
        account.setAccountStatus(AccountStatus.BLOCKED);
        accountRepository.save(account);
        log.info("Account Blocked {}", accountNumber);
    }

    public void deductBalance(String accountNumber, BigDecimal amount) {
        log.info("Deducting Balance {} from account: {}",amount, accountNumber);
        Account account =accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(()-> new RuntimeException("Account not found"));

        if (account.getAccountStatus() !=AccountStatus.ACTIVE){
            throw new RuntimeException("Account is not active " + accountNumber);
        }
        if(account.getBalance().compareTo(amount)<0){
            throw new RuntimeException("Insufficient funds fro account "+ accountNumber);
        }
        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        log.info("Balance updated, New Balance: {} ", account.getBalance());

    }

    public void creditBalance(String accountNumber, BigDecimal amount) {
        log.info("Crediting {} to account: {}", amount , accountNumber);

        Account account =accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(()-> new RuntimeException("Account not found"));

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        log.info("Balance Credited, New Balance : {}", account.getBalance());
    }
}
