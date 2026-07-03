package com.risk.dupdetect.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class TransactionKeyTest {

    @Test
    public void testReflexive() {
        TransactionKey key = new TransactionKey("payer1", "payee1", new BigDecimal("10.00"), "USD", "ref1");
        assertEquals(key, key);
    }

    @Test
    public void testSymmetric() {
        TransactionKey key1 = new TransactionKey("payer1", "payee1", new BigDecimal("10.00"), "USD", "ref1");
        TransactionKey key2 = new TransactionKey("payer1", "payee1", new BigDecimal("10.0"), "USD", "ref1");

        assertEquals(key1, key2);
        assertEquals(key2, key1);
        assertEquals(key1.hashCode(), key2.hashCode());
    }

    @Test
    public void testTransitive() {
        TransactionKey key1 = new TransactionKey("payer1", "payee1", new BigDecimal("10"), "USD", "ref1");
        TransactionKey key2 = new TransactionKey("payer1", "payee1", new BigDecimal("10.0"), "USD", "ref1");
        TransactionKey key3 = new TransactionKey("payer1", "payee1", new BigDecimal("10.00"), "USD", "ref1");

        assertEquals(key1, key2);
        assertEquals(key2, key3);
        assertEquals(key1, key3);

        assertEquals(key1.hashCode(), key2.hashCode());
        assertEquals(key2.hashCode(), key3.hashCode());
    }

    @Test
    public void testConsistent() {
        TransactionKey key1 = new TransactionKey("payer1", "payee1", new BigDecimal("10.00"), "USD", "ref1");
        TransactionKey key2 = new TransactionKey("payer1", "payee1", new BigDecimal("10.00"), "USD", "ref1");

        for (int i = 0; i < 10; i++) {
            assertEquals(key1, key2);
            assertEquals(key1.hashCode(), key2.hashCode());
        }
    }

    @Test
    public void testNullAndDifferentClass() {
        TransactionKey key = new TransactionKey("payer1", "payee1", new BigDecimal("10.00"), "USD", "ref1");
        assertNotEquals(null, key);
        assertNotEquals("string-object", key);
    }

    @Test
    public void testBigDecimalScaleTrap() {
        // BigDecimal scale trap: BigDecimal.equals() is scale-sensitive (10.0 != 10.00)
        // But our TransactionKey comparison should be scale-insensitive.
        TransactionKey key1 = new TransactionKey("payer1", "payee1", new BigDecimal("10.0"), "USD", "ref1");
        TransactionKey key2 = new TransactionKey("payer1", "payee1", new BigDecimal("10.00"), "USD", "ref1");
        TransactionKey key3 = new TransactionKey("payer1", "payee1", new BigDecimal("10"), "USD", "ref1");

        // Verify that equals returns true across all scales
        assertEquals(key1, key2);
        assertEquals(key2, key3);

        // Verify hash codes are identical
        assertEquals(key1.hashCode(), key2.hashCode());
        assertEquals(key2.hashCode(), key3.hashCode());

        // Verify HashSet logic (only one key should be stored)
        Set<TransactionKey> set = new HashSet<>();
        set.add(key1);
        set.add(key2);
        set.add(key3);

        assertEquals(1, set.size());
        assertTrue(set.contains(key1));
        assertTrue(set.contains(key2));
        assertTrue(set.contains(key3));
    }

    @Test
    public void testDifferentFieldsNotEqual() {
        TransactionKey base = new TransactionKey("payer1", "payee1", new BigDecimal("10.00"), "USD", "ref1");
        TransactionKey diffPayer = new TransactionKey("payer2", "payee1", new BigDecimal("10.00"), "USD", "ref1");
        TransactionKey diffPayee = new TransactionKey("payer1", "payee2", new BigDecimal("10.00"), "USD", "ref1");
        TransactionKey diffAmount = new TransactionKey("payer1", "payee1", new BigDecimal("20.00"), "USD", "ref1");
        TransactionKey diffCurrency = new TransactionKey("payer1", "payee1", new BigDecimal("10.00"), "EUR", "ref1");
        TransactionKey diffRef = new TransactionKey("payer1", "payee1", new BigDecimal("10.00"), "USD", "ref2");

        assertNotEquals(base, diffPayer);
        assertNotEquals(base, diffPayee);
        assertNotEquals(base, diffAmount);
        assertNotEquals(base, diffCurrency);
        assertNotEquals(base, diffRef);
    }
}
