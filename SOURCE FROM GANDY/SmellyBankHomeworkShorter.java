package edu.kettering.refactoring.bank;

import java.util.*;

/**
 * Refactoring Homework: Bank Accounts (Checking/Savings)
 *
 *
 * Smells included:
 *  - mysterious names, WITH comments: z, t, q
 *  - Duplicated code (reordered / slightly different)
 *  - Long function + long parameter list
 *  - Loop(s) that can be map/filter transforms
 *  - Data updates mixed with other commands 
 */
public class SmellyBankHomeworkShorter {

    // --- Domain (mutable on purpose) ---
    static abstract class BankAccount {
        private final String id;
        private final String owner;
        protected double balance; 
        private boolean flagged;

        protected BankAccount(String id, String owner, double bal) {
            this.id = id; this.owner = owner; this.balance = bal;
        }
        public String id() { return id; }
        public String owner() { return owner; }
        public double balance() { return balance; }
        public boolean flagged() { return flagged; }
        public void setFlagged(boolean v) { flagged = v; }
        public abstract String type();
    }

    static class CheckingAccount extends BankAccount {
        private final double overdraft;
        public CheckingAccount(String id, String owner, double bal, double overdraft) {
            super(id, owner, bal); this.overdraft = overdraft;
        }
        public double overdraft() { return overdraft; }
        public String type() { return "CHECKING"; }
    }

    static class SavingsAccount extends BankAccount {
        private final double rate; // kept as “dead data” on purpose (smell target for students)
        public SavingsAccount(String id, String owner, double bal, double rate) {
            super(id, owner, bal); this.rate = rate;
        }
        public double rate() { return rate; }
        public String type() { return "SAVINGS"; }
    }

    static class Txn {
        final String acctId, kind, memo; // kind: DEPOSIT/WITHDRAW ONLY
        final double amt;
        Txn(String acctId, String kind, double amt, String memo) {
            this.acctId = acctId; this.kind = kind; this.amt = amt; this.memo = memo;
        }
    }

    public static void main(String[] args) {
        List<BankAccount> accounts = new ArrayList<>();
        accounts.add(new CheckingAccount("C-100", "A. Chen", 250, 100));
        accounts.add(new SavingsAccount("S-200", "B. Patel", 1200, 0.02));
        accounts.add(new CheckingAccount("C-300", "C. Rivera", 40, 50));
        accounts.add(new SavingsAccount("S-400", "D. Smith", 9000, 0.03));

           // Five transactions chosen to exercise all behavior paths
        List<Txn> txns = List.of(
        // 1) Normal withdrawal from checking (allowed)
        new Txn("C-100", "WITHDRAW", 75, "ATM withdrawal"),

        // 2) Withdrawal from checking that exceeds overdraft (DECLINED)
        new Txn("C-300", "WITHDRAW", 120, "Billpay overdraft test"),

        // 3) Withdrawal from savings that would go negative (DECLINED)
        new Txn("S-200", "WITHDRAW", 1300, "Savings overdraft test"),

        // 4) Large deposit that triggers FLAG + VIP NOTE
        new Txn("S-400", "DEPOSIT", 1500, "Bonus deposit"),

        // 5) Small deposit to verify normal deposit path
        new Txn("C-100", "DEPOSIT", 25, "Cash deposit")
);
        System.out.println(processDailyBatch(
                accounts, txns,
                false,   // includeZeroAmountTxns
                1000.0,  // flagLargeTxnThreshold
                5000.0,  // vipBalanceThreshold
                true,    // debug
                "USD",   // currency
                2,       // digits
                true     // rounding
        ));
    }

    // Long function + long-ish parameter list + mixed responsibilities (intentionally)
    public static String processDailyBatch(
            List<BankAccount> inputAccounts,
            List<Txn> inputTxns,
            boolean includeZeroAmountTxns,
            double flagLargeTxnThreshold,
            double vipBalanceThreshold,
            boolean debug,
            String currency,
            int digits,
            boolean rounding
    ) {
        StringBuilder out = new StringBuilder();
        out.append("=== BANK BATCH REPORT ===\n");

        // loop -> map transform (accountId -> account)
        // store accounts by their id as the key
        Map<String, BankAccount> byId = new HashMap<>();
        for (BankAccount a : inputAccounts) byId.put(a.id(), a);

        // loop -> filter transform (include / exclude zero-amount txns)
        List<Txn> txns = new ArrayList<>();
        for (Txn x : inputTxns) {
            // if includeZeroAmountTxns is true OR the amount is not zero
            //then record the transaction
            if (includeZeroAmountTxns || x.amt != 0.0) txns.add(x);
            else if (debug) out.append("[dbg] filtered zero txn for ").append(x.acctId).append("\n");
        }

        // mysterious vars
        int z = 0;      // z = applied transaction count
        int q = 0;      // q = skipped transaction count
        double t = 0.0; // t = sum of absolute applied amounts (summary metric)

        out.append("\n-- APPLY --\n");
        for (Txn x : txns) {
            BankAccount a = byId.get(x.acctId);

            // if can't find bank account
            if (a == null) {
                q++;
                if (debug) out.append("[dbg] unknown ").append(x.acctId).append("\n");
                continue;
            }

            out.append(x.kind).append(" acct=").append(a.id())
                    .append(" owner=").append(a.owner())
                    .append(" amt=").append(fmt(x.amt, digits, rounding)).append(" ").append(currency)
                    .append(" memo=").append(x.memo).append("\n");

         
            if (x.kind.equals("DEPOSIT")) {
                // update the bank account balance with a deposit
                a.balance += x.amt; z++; t += Math.abs(x.amt);
                out.append("  newBal=").append(fmt(a.balance, digits, rounding)).append("\n");

            } else if (x.kind.equals("WITHDRAW")) {
                boolean ok;

                // update the bank account balance with a withdrawal
                if (a instanceof CheckingAccount c) ok = (a.balance - x.amt) >= -c.overdraft();
                else ok = (a.balance - x.amt) >= 0;

                if (!ok) { q++; out.append("  DECLINED\n"); }
                else {
                    a.balance -= x.amt; z++; t += Math.abs(x.amt);
                    out.append("  newBal=").append(fmt(a.balance, digits, rounding)).append("\n");
                }

            } else {
                q++;
                out.append("  SKIP unknown kind\n");
            }

            if (Math.abs(x.amt) >= flagLargeTxnThreshold) {
                a.setFlagged(true);
                out.append("  ** FLAG large txn **\n");
            }
            if (a.balance() >= vipBalanceThreshold) out.append("  VIP NOTE\n");
            out.append("\n");
        }

        out.append("-- POST-CHECKS --\n");
        for (BankAccount a : inputAccounts) {
            if (a instanceof CheckingAccount c) {
                if (a.balance() < -c.overdraft()) { a.setFlagged(true); out.append("Flag ").append(a.id()).append(" beyond overdraft\n"); }
            } else {
                if (a.balance() < 0) { a.setFlagged(true); out.append("Flag ").append(a.id()).append(" negative savings\n"); }
            }
        }


        out.append("\n-- SUMMARY A --\n");
        for (BankAccount a : inputAccounts)
            out.append(a.id()).append(" ").append(a.type()).append(" ").append(a.owner())
                    .append(" bal=").append(fmt(a.balance(), digits, rounding))
                    .append(a.flagged() ? " [FLAG]" : "").append("\n");

        out.append("\n-- TOTALS --\n");
        out.append("applied=").append(z).append(" skipped=").append(q)
                .append(" absTotal=").append(fmt(t, digits, rounding)).append(" ").append(currency).append("\n");

        out.append("\n-- SUMMARY B --\n");
        for (int i = inputAccounts.size() - 1; i >= 0; i--) {
            BankAccount a = inputAccounts.get(i);
            out.append("[").append(a.type()).append("] ").append(a.owner())
                    .append(" id=").append(a.id())
                    .append(" bal=").append(fmt(a.balance(), digits, rounding))
                    .append(a.flagged() ? " *" : "").append("\n");
        }

        return out.toString();
    }

    static String fmt(double v, int digits, boolean rounding) {
        if (!rounding) return Double.toString(v);
        double f = Math.pow(10, digits);
        double r = Math.round(v * f) / f;
        return String.format(Locale.US, "%." + digits + "f", r);
    }
}
