package com.playmate.space.service.finance;

import com.playmate.space.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExpenseCalculatorTest {
    private ExpenseCalculator.Share share(long id,String amount,String ratio) { return new ExpenseCalculator.Share(id,amount==null?null:new BigDecimal(amount),ratio==null?null:new BigDecimal(ratio)); }
    @Test void equalSplitAssignsCentsByStableMemberId() {
        var result=ExpenseCalculator.split(new BigDecimal("100.00"),"EQUAL",List.of(share(3,null,null),share(1,null,null),share(2,null,null)));
        assertEquals(new BigDecimal("33.34"),result.get(1L)); assertEquals(new BigDecimal("33.33"),result.get(2L));
    }
    @Test void proportionalUsesExactRemaindersAndNeverLosesCents() {
        var result=ExpenseCalculator.split(new BigDecimal("0.01"),"PROPORTIONAL",List.of(share(1,null,"999999999999.9998"),share(2,null,"999999999999.9999")));
        assertEquals(new BigDecimal("0.01"),result.get(2L)); assertEquals(new BigDecimal("0.00"),result.get(1L));
        Random random=new Random(42);
        for(int i=0;i<500;i++) {
            var amount=BigDecimal.valueOf(1+random.nextInt(10000000),2);
            List<ExpenseCalculator.Share> shares=new ArrayList<>();
            for(long id=1;id<=2+random.nextInt(30);id++) shares.add(new ExpenseCalculator.Share(id,null,BigDecimal.valueOf(1+random.nextInt(100000),4)));
            var allocation=ExpenseCalculator.split(amount,"PROPORTIONAL",shares);
            assertEquals(amount,allocation.values().stream().reduce(new BigDecimal("0.00"),BigDecimal::add));
            assertTrue(allocation.values().stream().allMatch(a -> a.signum()>=0));
        }
    }
    @Test void customAndInvalidInputs() {
        assertThrows(BusinessException.class,() -> ExpenseCalculator.split(new BigDecimal("10"),"CUSTOM",List.of(share(1,"9",null))));
        assertThrows(BusinessException.class,() -> ExpenseCalculator.split(new BigDecimal("10"),"EQUAL",List.of(share(1,null,null),share(1,null,null))));
        assertThrows(BusinessException.class,() -> ExpenseCalculator.split(new BigDecimal("10"),"PROPORTIONAL",List.of(share(1,null,"0"))));
        assertThrows(BusinessException.class,() -> ExpenseCalculator.split(new BigDecimal("10.001"),"EQUAL",List.of(share(1,null,null))));
        assertEquals(new BigDecimal("10.00"),ExpenseCalculator.split(new BigDecimal("10"),"CUSTOM",List.of(share(1,"0",null),share(2,"10",null))).get(2L));
    }
    @Test void dinnerTeaTaxiSettleToCorrectTransfersAndDoNotMutateInput() {
        var balances=new TreeMap<>(Map.of(1L,new BigDecimal("182.00"),2L,new BigDecimal("-97.00"),3L,new BigDecimal("-85.00")));
        var original=new TreeMap<>(balances);
        var transfers=ExpenseCalculator.settle(balances);
        assertEquals(List.of(new ExpenseCalculator.Transfer(2L,1L,new BigDecimal("97.00")),new ExpenseCalculator.Transfer(3L,1L,new BigDecimal("85.00"))),transfers);
        assertEquals(original,balances);
        transfers.forEach(t -> { balances.merge(t.fromId(),t.amount(),BigDecimal::add); balances.merge(t.toId(),t.amount().negate(),BigDecimal::add); });
        assertTrue(balances.values().stream().allMatch(n -> n.signum()==0));
        assertThrows(IllegalArgumentException.class,() -> ExpenseCalculator.settle(Map.of(1L,BigDecimal.ONE)));
    }
}
