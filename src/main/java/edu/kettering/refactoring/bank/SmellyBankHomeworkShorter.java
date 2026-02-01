package edu.kettering.refactoring.bank;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Refactored Bank Batch Processing
 * - Clear domain behavior
 * - No duplicated logic
 * - No mysterious variables
 * - Smaller methods
 * - Enum-based transactions
 * - Policy + formatting configs
 */
public class SmellyBankHomeworkShorter {

    /*
     * =======================
     * Domain Model
     * =======================
     */

    static abstract class BankAccount {
        private final String id;
        private final String owner;
        protected double balance;
        private boolean flagged;

        protected BankAccount(String id, String owner, double balance) {
            this.id = id;
            this.owner = owner;
            this.balance = balance;
        }

        public String id() {
            return id;
        }

        public String owner() {
            return owner;
        }

        public double balance() {
            return balance;
        }

        public boolean flagged() {
            return flagged;
        }

        public void flag() {
            flagged = true;
        }

        abstract String type();

        abstract boolean canWithdraw(double amount);

        abstract boolean invalidBalance();

        void deposit(double amount) {
            balance += amount;
        }

        void withdraw(double amount) {
            balance -= amount;
        }
    }

    static class CheckingAccount extends BankAccount {
        private final double overdraft;

        CheckingAccount(String id, String owner, double balance, double overdraft) {
            super(id, owner, balance);
            this.overdraft = overdraft;
        }

        @Override
        boolean canWithdraw(double amount) {
            return balance - amount >= -overdraft;
        }

        @Override
        boolean invalidBalance() {
            return balance < -overdraft;
        }

        @Override
        String type() {
            return "CHECKING";
        }
    }

    static class SavingsAccount extends BankAccount {
        private final double rate; // intentionally unused (dead data smell removed from logic)

        SavingsAccount(String id, String owner, double balance, double rate) {
            super(id, owner, balance);
            this.rate = rate;
        }

        @Override
        boolean canWithdraw(double amount) {
            return balance - amount >= 0;
        }

        @Override
        boolean invalidBalance() {
            return balance < 0;
        }

        @Override
        String type() {
            return "SAVINGS";
        }
    }

    enum TxnType {
        DEPOSIT, WITHDRAW
    }

    static class Txn {
        final String accountId;
        final TxnType type;
        final double amount;
        final String memo;

        Txn(String accountId, TxnType type, double amount, String memo) {
            this.accountId = accountId;
            this.type = type;
            this.amount = amount;
            this.memo = memo;
        }
    }

    /*
     * =======================
     * Configuration Objects
     * =======================
     */

    record PolicyConfig(
            boolean includeZeroAmountTxns,
            double largeTxnThreshold,
            double vipBalanceThreshold) {
    }

    record FormatConfig(
            boolean debug,
            String currency,
            int digits,
            boolean rounding) {
    }

    record BatchStats(
            int applied,
            int skipped,
            double absTotal) {
        BatchStats addApplied(double amt) {
            return new BatchStats(applied + 1, skipped, absTotal + Math.abs(amt));
        }

        BatchStats addSkipped() {
            return new BatchStats(applied, skipped + 1, absTotal);
        }
    }

    /*
     * =======================
     * Batch Processing
     * =======================
     */

    public static String processDailyBatch(
            List<BankAccount> accounts,
            List<Txn> txns,
            PolicyConfig policy,
            FormatConfig fmt) {
        StringBuilder out = new StringBuilder();
        out.append("=== BANK BATCH REPORT ===\n");

        Map<String, BankAccount> accountIndex = indexAccounts(accounts);
        List<Txn> filteredTxns = filterTransactions(txns, policy, fmt, out);

        BatchStats stats = applyTransactions(filteredTxns, accountIndex, policy, fmt, out);
        postCheckAccounts(accounts, out);

        appendSummaryA(accounts, fmt, out);
        appendTotals(stats, fmt, out);
        appendSummaryB(accounts, fmt, out);

        return out.toString();
    }

    /*
     * =======================
     * Helpers
     * =======================
     */

    private static Map<String, BankAccount> indexAccounts(List<BankAccount> accounts) {
        return accounts.stream()
                .collect(Collectors.toMap(BankAccount::id, a -> a));
    }

    private static List<Txn> filterTransactions(
            List<Txn> txns,
            PolicyConfig policy,
            FormatConfig fmt,
            StringBuilder out) {
        return txns.stream()
                .filter(t -> {
                    boolean keep = policy.includeZeroAmountTxns() || t.amount != 0.0;
                    if (!keep && fmt.debug()) {
                        out.append("[dbg] filtered zero txn for ").append(t.accountId).append("\n");
                    }
                    return keep;
                })
                .toList();
    }

    private static BatchStats applyTransactions(
            List<Txn> txns,
            Map<String, BankAccount> accounts,
            PolicyConfig policy,
            FormatConfig fmt,
            StringBuilder out) {
        BatchStats stats = new BatchStats(0, 0, 0);
        out.append("\n-- APPLY --\n");

        for (Txn t : txns) {
            BankAccount acct = accounts.get(t.accountId);

            if (acct == null) {
                stats = stats.addSkipped();
                if (fmt.debug())
                    out.append("[dbg] unknown ").append(t.accountId).append("\n");
                continue;
            }

            out.append(t.type).append(" acct=").append(acct.id())
                    .append(" owner=").append(acct.owner())
                    .append(" amt=").append(format(t.amount, fmt))
                    .append(" ").append(fmt.currency())
                    .append(" memo=").append(t.memo).append("\n");

            boolean applied = switch (t.type) {
                case DEPOSIT -> {
                    acct.deposit(t.amount);
                    yield true;
                }
                case WITHDRAW -> acct.canWithdraw(t.amount) && applyWithdrawal(acct, t.amount);
            };

            if (!applied) {
                stats = stats.addSkipped();
                out.append("  DECLINED\n\n");
                continue;
            }

            stats = stats.addApplied(t.amount);
            out.append("  newBal=").append(format(acct.balance(), fmt)).append("\n");

            if (Math.abs(t.amount) >= policy.largeTxnThreshold()) {
                acct.flag();
                out.append("  ** FLAG large txn **\n");
            }

            if (acct.balance() >= policy.vipBalanceThreshold()) {
                out.append("  VIP NOTE\n");
            }

            out.append("\n");
        }

        return stats;
    }

    private static boolean applyWithdrawal(BankAccount acct, double amount) {
        acct.withdraw(amount);
        return true;
    }

    private static void postCheckAccounts(List<BankAccount> accounts, StringBuilder out) {
        out.append("-- POST-CHECKS --\n");
        for (BankAccount a : accounts) {
            if (a.invalidBalance()) {
                a.flag();
                out.append("Flag ").append(a.id()).append(" invalid balance\n");
            }
        }
    }

    private static void appendSummaryA(List<BankAccount> accounts, FormatConfig fmt, StringBuilder out) {
        out.append("\n-- SUMMARY A --\n");
        for (BankAccount a : accounts) {
            out.append(a.id()).append(" ").append(a.type())
                    .append(" ").append(a.owner())
                    .append(" bal=").append(format(a.balance(), fmt))
                    .append(a.flagged() ? " [FLAG]" : "")
                    .append("\n");
        }
    }

    private static void appendSummaryB(List<BankAccount> accounts, FormatConfig fmt, StringBuilder out) {
        out.append("\n-- SUMMARY B --\n");
        ListIterator<BankAccount> it = accounts.listIterator(accounts.size());
        while (it.hasPrevious()) {
            BankAccount a = it.previous();
            out.append("[").append(a.type()).append("] ")
                    .append(a.owner())
                    .append(" id=").append(a.id())
                    .append(" bal=").append(format(a.balance(), fmt))
                    .append(a.flagged() ? " *" : "")
                    .append("\n");
        }
    }

    private static void appendTotals(BatchStats stats, FormatConfig fmt, StringBuilder out) {
        out.append("\n-- TOTALS --\n");
        out.append("applied=").append(stats.applied())
                .append(" skipped=").append(stats.skipped())
                .append(" absTotal=").append(format(stats.absTotal(), fmt))
                .append(" ").append(fmt.currency()).append("\n");
    }

    private static String format(double value, FormatConfig fmt) {
        if (!fmt.rounding())
            return Double.toString(value);
        double factor = Math.pow(10, fmt.digits());
        double rounded = Math.round(value * factor) / factor;
        return String.format(Locale.US, "%." + fmt.digits() + "f", rounded);
    }

    public static void main(String[] args) {
        // 1) Setup accounts
        List<BankAccount> accounts = new ArrayList<>();
        accounts.add(new CheckingAccount("C-100", "A. Chen", 250, 100));
        accounts.add(new SavingsAccount("S-200", "B. Patel", 1200, 0.02));
        accounts.add(new CheckingAccount("C-300", "C. Rivera", 40, 50));
        accounts.add(new SavingsAccount("S-400", "D. Smith", 9000, 0.03));

        // 2) Setup transactions
        List<Txn> txns = List.of(
                new Txn("C-100", TxnType.WITHDRAW, 75, "ATM withdrawal"),
                new Txn("C-300", TxnType.WITHDRAW, 120, "Billpay overdraft test"),
                new Txn("S-200", TxnType.WITHDRAW, 1300, "Savings overdraft test"),
                new Txn("S-400", TxnType.DEPOSIT, 1500, "Bonus deposit"),
                new Txn("C-100", TxnType.DEPOSIT, 25, "Cash deposit"));

        // 3) Setup policy and formatting
        PolicyConfig policy = new PolicyConfig(
                false, // includeZeroAmountTxns
                1000.0, // largeTxnThreshold
                5000.0 // vipBalanceThreshold
        );

        FormatConfig format = new FormatConfig(
                true, // debug
                "USD", // currency
                2, // digits
                true // rounding
        );

        // 4) Process batch and print report
        String report = processDailyBatch(accounts, txns, policy, format);
        System.out.println(report);
    }
}
