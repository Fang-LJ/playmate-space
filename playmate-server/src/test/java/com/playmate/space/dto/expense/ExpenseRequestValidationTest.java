package com.playmate.space.dto.expense;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpenseRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void amountHonorsDecimalTwelveTwoRange() {
        assertFalse(hasViolation(request("9999999999.99", share("1.00", null)), "amount"));
        assertTrue(hasViolation(request("10000000000.00", share("1.00", null)), "amount"));
        assertTrue(hasViolation(request("1.001", share("1.00", null)), "amount"));
    }

    @Test
    void customShareAmountHonorsDecimalTwelveTwoRange() {
        assertFalse(hasViolation(request("1.00", share("9999999999.99", null)), "shareAmount"));
        assertTrue(hasViolation(request("1.00", share("10000000000.00", null)), "shareAmount"));
        assertTrue(hasViolation(request("1.00", share("1.001", null)), "shareAmount"));
    }

    @Test
    void splitRatioHonorsDecimalSixteenFourRange() {
        assertFalse(hasViolation(request("1.00", share(null, "999999999999.9999")), "splitRatio"));
        assertTrue(hasViolation(request("1.00", share(null, "1000000000000.0000")), "splitRatio"));
        assertTrue(hasViolation(request("1.00", share(null, "1.00001")), "splitRatio"));
    }

    private SaveExpenseRequest request(String amount, ExpenseShareRequest share) {
        return new SaveExpenseRequest("测试", "FOOD", new BigDecimal(amount), 1L,
                LocalDateTime.of(2026, 8, 20, 12, 0), "EQUAL", List.of(share),
                null, null, "validation-test", null);
    }

    private ExpenseShareRequest share(String amount, String ratio) {
        return new ExpenseShareRequest(1L, amount == null ? null : new BigDecimal(amount),
                ratio == null ? null : new BigDecimal(ratio));
    }

    private boolean hasViolation(SaveExpenseRequest request, String property) {
        Set<ConstraintViolation<SaveExpenseRequest>> violations = validator.validate(request);
        return violations.stream().anyMatch(item -> item.getPropertyPath().toString().contains(property));
    }
}
