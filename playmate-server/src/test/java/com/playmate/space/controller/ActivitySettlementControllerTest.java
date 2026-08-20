package com.playmate.space.controller;

import com.playmate.space.common.exception.BusinessException;
import com.playmate.space.dto.expense.CancelSettlementRequest;
import com.playmate.space.dto.expense.CompleteSettlementRequest;
import com.playmate.space.dto.expense.SettlementHistoryResponse;
import com.playmate.space.service.SettlementService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ActivitySettlementControllerTest {
    private static final Long ACTIVITY_ID = 10L;

    @Test
    void transferWritesAreDisabledByDefault() {
        SettlementService service = mock(SettlementService.class);
        ActivitySettlementController controller = new ActivitySettlementController(service, false);
        CompleteSettlementRequest complete = new CompleteSettlementRequest(1L, 2L, new BigDecimal("10.00"), null);

        BusinessException completeError = assertThrows(BusinessException.class,
                () -> controller.complete(ACTIVITY_ID, complete));
        BusinessException cancelError = assertThrows(BusinessException.class,
                () -> controller.cancel(ACTIVITY_ID, 99L, new CancelSettlementRequest(null)));

        assertEquals("当前版本暂未开放转账状态功能", completeError.getMessage());
        assertEquals("当前版本暂未开放转账状态功能", cancelError.getMessage());
        verifyNoInteractions(service);
    }

    @Test
    void enabledFlagKeepsLegacyWriteDelegation() {
        SettlementService service = mock(SettlementService.class);
        ActivitySettlementController controller = new ActivitySettlementController(service, true);
        CompleteSettlementRequest complete = new CompleteSettlementRequest(1L, 2L, new BigDecimal("10.00"), null);
        CancelSettlementRequest cancel = new CancelSettlementRequest("测试");
        SettlementHistoryResponse completed = history(1L, "COMPLETED");
        SettlementHistoryResponse canceled = history(1L, "CANCELED");
        when(service.complete(ACTIVITY_ID, complete)).thenReturn(completed);
        when(service.cancel(ACTIVITY_ID, 1L, cancel)).thenReturn(canceled);

        assertEquals(completed, controller.complete(ACTIVITY_ID, complete).data());
        assertEquals(canceled, controller.cancel(ACTIVITY_ID, 1L, cancel).data());
        verify(service).complete(ACTIVITY_ID, complete);
        verify(service).cancel(ACTIVITY_ID, 1L, cancel);
    }

    private SettlementHistoryResponse history(Long id, String status) {
        return new SettlementHistoryResponse(id, 1L, "A", 2L, "B", new BigDecimal("10.00"),
                status, null, null, 1L, null);
    }
}
