package com.risk.dupdetect.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.risk.dupdetect.domain.Transaction;
import com.risk.dupdetect.domain.TransactionStatus;
import com.risk.dupdetect.dto.request.TransactionRequest;
import com.risk.dupdetect.repository.DuplicateRecordRepository;
import com.risk.dupdetect.repository.TransactionRepository;
import com.risk.dupdetect.service.BloomFilterService;
import com.risk.dupdetect.service.SlidingWindowStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private DuplicateRecordRepository duplicateRecordRepository;

    @Autowired
    private BloomFilterService bloomFilterService;

    @Autowired
    private SlidingWindowStore slidingWindowStore;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    public void cleanUp() {
        duplicateRecordRepository.deleteAll();
        transactionRepository.deleteAll();
        slidingWindowStore.clear();
        bloomFilterService.reset();
    }

    @Test
    public void testSubmitTransactionAndGetById() throws Exception {
        TransactionRequest request = new TransactionRequest(
                "payer-111",
                "payee-222",
                new BigDecimal("500.00"),
                "USD",
                "idemp-key-111"
        );

        String jsonResponse = mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.payerId", is("payer-111")))
                .andExpect(jsonPath("$.payeeId", is("payee-222")))
                .andExpect(jsonPath("$.amount", is(500.00)))
                .andExpect(jsonPath("$.currency", is("USD")))
                .andExpect(jsonPath("$.status", is("POSTED")))
                .andReturn().getResponse().getContentAsString();

        // Extract ID
        String idStr = objectMapper.readTree(jsonResponse).get("id").asText();
        UUID id = UUID.fromString(idStr);

        // Fetch transaction by ID
        mockMvc.perform(get("/api/v1/transactions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(idStr)))
                .andExpect(jsonPath("$.payerId", is("payer-111")))
                .andExpect(jsonPath("$.status", is("POSTED")));
    }

    @Test
    public void testDuplicateSubmissionConflict() throws Exception {
        TransactionRequest request = new TransactionRequest(
                "payer-999",
                "payee-888",
                new BigDecimal("99.95"),
                "USD",
                "retry-key-abc"
        );

        // Submit first (succeeds)
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Submit second (fails with 409 Conflict)
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("Conflict")))
                .andExpect(jsonPath("$.message", containsString("duplicate transaction detected")));
    }

    @Test
    public void testGetTransactionsFilterAndPage() throws Exception {
        // Insert sample transaction directly
        Transaction txn1 = Transaction.builder()
                .payerId("payer-abc")
                .payeeId("payee-xyz")
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .status(TransactionStatus.POSTED)
                .build();
        transactionRepository.save(txn1);

        mockMvc.perform(get("/api/v1/transactions")
                        .param("payerId", "payer-abc")
                        .param("status", "POSTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].payerId", is("payer-abc")))
                .andExpect(jsonPath("$.content[0].status", is("POSTED")));
    }

    @Test
    public void testGetWindowStatus() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/window/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size", is(0)))
                .andExpect(jsonPath("$.storeType", is("inmemory")));
    }

    @Test
    public void testSubmitValidationErrors() throws Exception {
        // Submit empty/invalid request
        TransactionRequest invalidRequest = new TransactionRequest(
                "", "", new BigDecimal("-10.00"), "US", null
        );

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.validationErrors.payerId", notNullValue()))
                .andExpect(jsonPath("$.validationErrors.payeeId", notNullValue()))
                .andExpect(jsonPath("$.validationErrors.amount", notNullValue()))
                .andExpect(jsonPath("$.validationErrors.currency", notNullValue()));
    }
}
