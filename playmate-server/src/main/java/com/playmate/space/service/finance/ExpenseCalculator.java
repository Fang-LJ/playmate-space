package com.playmate.space.service.finance;

import com.playmate.space.common.exception.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Pure, identity-agnostic calculation shared by activity expenses and standalone books. */
public final class ExpenseCalculator {
    private ExpenseCalculator() {}
    public record Share(Long participantId, BigDecimal amount, BigDecimal ratio) {}
    public record Transfer(Long fromId, Long toId, BigDecimal amount) {}
    private record Allocation(Long id, long cents, BigDecimal remainder) {}

    public static Map<Long, BigDecimal> split(BigDecimal amount, String mode, List<Share> input) {
        if (amount == null || amount.signum() <= 0 || input == null || input.isEmpty()) throw invalid("请输入金额并选择分摊成员");
        long total;
        try { total = amount.movePointRight(2).longValueExact(); }
        catch (ArithmeticException e) { throw invalid("金额最多保留两位小数"); }
        List<Share> shares = input.stream().sorted(Comparator.comparing(Share::participantId)).toList();
        if (shares.stream().map(Share::participantId).distinct().count() != shares.size()) throw invalid("分摊成员不能重复");
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        if ("CUSTOM".equals(mode)) {
            BigDecimal sum = BigDecimal.ZERO;
            for (Share share : shares) {
                if (share.amount() == null || share.amount().signum() < 0) throw invalid("分摊金额不能为空或小于零");
                BigDecimal value;
                try { value = share.amount().setScale(2, RoundingMode.UNNECESSARY); }
                catch (ArithmeticException e) { throw invalid("分摊金额最多保留两位小数"); }
                result.put(share.participantId(), value); sum = sum.add(value);
            }
            if (sum.compareTo(amount) != 0) throw invalid("自定义分摊金额之和必须等于总金额");
        } else if ("EQUAL".equals(mode)) {
            for (int i = 0; i < shares.size(); i++) result.put(shares.get(i).participantId(), BigDecimal.valueOf(total / shares.size() + (i < total % shares.size() ? 1 : 0), 2));
        } else if ("PROPORTIONAL".equals(mode)) {
            if (shares.stream().anyMatch(s -> s.ratio() == null || s.ratio().signum() <= 0 || s.ratio().scale() > 4)) throw invalid("比例必须大于零且最多保留四位小数");
            BigDecimal denominator = shares.stream().map(Share::ratio).reduce(BigDecimal.ZERO, BigDecimal::add);
            List<Allocation> allocations = new ArrayList<>(); long assigned = 0;
            for (Share share : shares) {
                // Exact remainders avoid rounding changing the order of nearly equal ratios.
                BigDecimal[] parts = BigDecimal.valueOf(total).multiply(share.ratio()).divideAndRemainder(denominator);
                long cents = parts[0].longValueExact(); assigned += cents;
                allocations.add(new Allocation(share.participantId(), cents, parts[1]));
            }
            allocations.sort(Comparator.comparing(Allocation::remainder).reversed().thenComparing(Allocation::id));
            long remaining = total - assigned;
            for (int i = 0; i < allocations.size(); i++) {
                Allocation a = allocations.get(i);
                result.put(a.id(), BigDecimal.valueOf(a.cents() + (i < remaining ? 1 : 0), 2));
            }
        } else throw invalid("不支持的分摊方式");
        return result;
    }

    public static List<Transfer> settle(Map<Long, BigDecimal> balances) {
        if (balances.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).signum() != 0) throw new IllegalArgumentException("Unbalanced expense ledger");
        var remaining = new TreeMap<>(balances);
        var debtors = remaining.keySet().stream().filter(id -> remaining.get(id).signum() < 0).toList();
        var creditors = remaining.keySet().stream().filter(id -> remaining.get(id).signum() > 0).toList();
        List<Transfer> result = new ArrayList<>(); int d = 0, c = 0;
        while (d < debtors.size() && c < creditors.size()) {
            Long from = debtors.get(d), to = creditors.get(c);
            BigDecimal amount = remaining.get(from).abs().min(remaining.get(to)).setScale(2, RoundingMode.UNNECESSARY);
            result.add(new Transfer(from, to, amount));
            remaining.put(from, remaining.get(from).add(amount)); remaining.put(to, remaining.get(to).subtract(amount));
            if (remaining.get(from).signum() == 0) d++;
            if (remaining.get(to).signum() == 0) c++;
        }
        return result;
    }
    private static BusinessException invalid(String message) { return new BusinessException("PARAM_ERROR", message); }
}
