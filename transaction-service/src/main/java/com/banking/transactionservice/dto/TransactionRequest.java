package com.banking.transactionservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class TransactionRequest {

    @NotNull(message = "Sender Account Number is Required")
    private String senderAccountNumber;

    @NotNull(message = "Reciever Account Nymber is Required")
    private String recieverAccountNumber;

    @NotNull(message = "Amount is Required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    private String description;
}
