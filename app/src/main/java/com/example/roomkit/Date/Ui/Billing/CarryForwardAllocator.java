package com.example.roomkit.Date.Billing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Whole-KHR allocation only. Callers supply validated, oldest-first invoices. */
public final class CarryForwardAllocator {
    private CarryForwardAllocator() {}
    public static final class Balance {
        public final long invoiceId;
        public final long balanceKhr;
        public Balance(long invoiceId, long balanceKhr) {
            if (invoiceId <= 0 || balanceKhr < 0) throw new IllegalArgumentException("Invalid invoice balance.");
            this.invoiceId = invoiceId;
            this.balanceKhr = balanceKhr;
        }
    }
    public static final class Allocation {
        public final long invoiceId;
        public final long amountKhr;
        Allocation(long invoiceId, long amountKhr) {
            this.invoiceId = invoiceId;
            this.amountKhr = amountKhr;
        }
    }
    public static long total(List<Balance> ordered) {
        long total = 0;
        Set<Long> seen = new HashSet<>();
        for (Balance row : ordered) {
            if (!seen.add(row.invoiceId)) throw new IllegalArgumentException("Duplicate invoice.");
            total = Math.addExact(total, row.balanceKhr);
        }
        return total;
    }
    public static List<Allocation> allocate(List<Balance> ordered, long amountKhr) {
        long total = total(ordered);
        if (amountKhr <= 0 || amountKhr > total)
            throw new IllegalArgumentException("Payment must be positive and cannot exceed the total to pay.");
        long remaining = amountKhr;
        List<Allocation> result = new ArrayList<>();
        for (Balance row : ordered) {
            long applied = Math.min(remaining, row.balanceKhr);
            if (applied > 0) result.add(new Allocation(row.invoiceId, applied));
            remaining = Math.subtractExact(remaining, applied);
            if (remaining == 0) break;
        }
        if (remaining != 0) throw new IllegalStateException("Unallocated payment amount.");
        return result;
    }
}
