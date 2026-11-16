package com.banking.paymentservice.service;

import com.banking.paymentservice.dto.CreatePaymentRequest;
import com.banking.paymentservice.dto.PaymentOrderResponse;
import com.banking.paymentservice.entity.Payment;
import com.banking.paymentservice.entity.PaymentStatus;
import com.banking.paymentservice.repository.PaymentRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;

    @Value("${razorpay.key-id}")
    private String keyId;
    @Value("${razorpay.key-secret}")
    private String keySecret;

    private final KafkaTemplate<String,Object> kafkaTemplate;

    private static final String PAYMENT_COMPLETED_TOPIC = "payment.completed";
    private static final String PAYMENT_FAILED_TOPIC = "payment.failed";

    public PaymentOrderResponse createPaymentOrder(@Valid CreatePaymentRequest request) throws RazorpayException {
        log.info("Creating payment order for account: {} amount: {}",request.getAccountNumber() ,request.getAmount());

        RazorpayClient razorpayClient = new RazorpayClient(keyId,keySecret);
        int convertedAmount = request.getAmount()
                .multiply(BigDecimal.valueOf(100))
                .intValue();

        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", convertedAmount);
        orderRequest.put("currency", "USD/INR");
        orderRequest.put("receipt", "rcpt_"+ System.currentTimeMillis()+ UUID.randomUUID().toString()
                .replace("-","").substring(0,10));

        Order razorpayOder = razorpayClient.orders.create(orderRequest);
        log.info("Razorpay order created: {}", razorpayOder.get("id").toString());

        Payment payment = new Payment();
        payment.setRazorpatPaymentsId(razorpayOder.get("id").toString());
        payment.setAccountNumber(request.getAccountNumber());
        payment.setAmount(request.getAmount());
        payment.setStatus(PaymentStatus.CREATED);
        payment.setDescription(request.getDescription());

        Payment savePayment = paymentRepository.save(payment);

        return new PaymentOrderResponse(
                savePayment.getId(),
                razorpayOder.get("id").toString(),
                request.getAmount(),
                "INR",
                "CREATED",
                keyId
        );

    }

    public void handleWebhook(Map<String, Object> payload) {
        log.info("Received razorpay webhook: {}", payload.get("event"));

        String event = (String) payload.get("event");

        if("payment.captured".equals(event)){
            handlePaymentSuccess(payload);
        }
        else if("payment.failed".equals(event)){
            handlePaymentFailure(payload);
        }
    }

    private void handlePaymentFailure(Map<String, Object> payload) {

        try{
            Map<String , Object> paymentData = extractPaymentData(payload);
            String orderId = (String) paymentData.get("order_id");

            Payment payment = paymentRepository.findByRazorpayOrderId(orderId)
                    .orElseThrow(()-> new RuntimeException("Payment not found for oder id "+orderId));

            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Payment failed via razorpay");
            paymentRepository.save(payment);

            Map<String , Object> event = new HashMap<>();
            event.put("paymentId",payment.getId());
            event.put("accountNumber", payment.getAccountNumber());
            event.put("amount", payment.getAmount());
            event.put("razorpayPaymentID","Payment failed via razorpay");

            kafkaTemplate.send(PAYMENT_FAILED_TOPIC,payment.getId(),event);
            log.warn("Payment faild: {}",payment.getId());
        } catch (Exception e) {
            log.error("Error handling payment failure: {}",e.getMessage());
        }
    }

    private void handlePaymentSuccess(Map<String, Object> payload) {
        try{
            Map<String , Object> paymentData = extractPaymentData(payload);
            String orderId = (String) paymentData.get("order_id");
            String paymentId =(String) paymentData.get("id");

            Payment payment = paymentRepository.findByRazorpayOrderId(orderId)
                    .orElseThrow(()-> new RuntimeException("Payment not found for oder id "+orderId));
            payment.setRazorpayOrderId(paymentId);
            payment.setStatus(PaymentStatus.COMPLETED);
            paymentRepository.save(payment);

            Map<String , Object> event = new HashMap<>();
            event.put("paymentId",payment.getId());
            event.put("accountNumber", payment.getAccountNumber());
            event.put("amount", payment.getAmount());
            event.put("razorpayPaymentID",paymentId);

            kafkaTemplate.send(PAYMENT_COMPLETED_TOPIC,payment.getId(),event);
            log.info("Payment completed: {}",payment.getId());
        } catch (Exception e) {
            log.error("Error handling payment success: {}",e.getMessage());
        }
    }

    private Map<String, Object> extractPaymentData(Map<String, Object> payload) {
        Map<String,Object> entity = (Map<String, Object>) payload.get("payload");

        Map<String,Object> paymentwrapper = (Map<String, Object>) entity.get("payment");

        return (Map<String, Object>) paymentwrapper.get("entity");
    }
}
