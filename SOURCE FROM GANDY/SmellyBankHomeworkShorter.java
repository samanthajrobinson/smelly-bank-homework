package edu.kettering.refactoring.bank;

import java.util.*;
import java.util.stream.Collectors;

public class SmellyBankHomeworkShorter {

    /* =======================
       Domain Model
       ======================= */

    static abstract class BankAccount {
        private final String id;
        private final String owner;
        protected double balance;
        private boolean flagged = false;

        protected BankAccount(String id, String owner, double balance) {
            this.id = id;
            this.owner = owner;
            this.balance = balance;
        }

        public String id() { return id; }
        public String owner() { return owner; }
        public double balance() { return balance; }
        public boolean flagged() { return flagged; }

        public void deposit(double amt) { balance += amt; }

        public boolean withdraw(double amt) {
            if (!canWithdraw(amt)) return false;
            balance -= amt;
            return true;
        }

        public void flag() { flagged = true; }

        abstract boolean canWithdraw(double amt);
        abstract boolean invalidBalance();
        abstract String type();
    }

    static class CheckingAccount extends BankAccount {
        private final double overdraft;

        CheckingAccount(String id, String owner, double bal, double overdraft) {
            super(id, owner, bal);
            this.overdraft = overdraft;
        }

        boolean canWithdraw(double amt) {
            return balance - amt >= -overdraft;
        }

        boolean invalidBalance() {
            return balance < -overdraft;
        }

        String type() {
            return "CHECKING";
        }
    }

    static class SavingsAccount extends BankAccount {
        private final double rate;

        SavingsAccount(String id, String owner, double bal, double rate) {
            super(id, owner, bal);
            this.rate = rate;
        }

        boolean canWithdraw(double amt) {
            return balance - amt >= 0;
        }

        boolean invalidBalance() {
            return balance < 0;
        }

        String type() {
            return "SAVINGS";
        }
    }

    /* =======================
       Transactions
       ======================= */

    enum TxnType { DEPOSIT, WITHDRAW }

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

    /* =======================
       Configuration
       ======================= */

    record PolicyConfig(
            boolean includeZeroAmountTxns,
            double largeTxnThreshold,
            double vipBalanceThreshold
    ) {}

    record FormatConfig(
            boolean debug,
            String currency,
            int digits,
            boolean rounding
    ) {}

    /* =======================
       Supporting Services
       ======================= */

    static class MoneyFormatter {
        private final FormatConfig cfg;

        MoneyFormatter(FormatConfig cfg) {
            this.cfg = cfg;
        }

        String format(double v) {
            if (!cfg.rounding()) return Double.toString(v);
            double f = Math.pow(10, cfg.digits());
            return String.format(
                    Locale.US,
                    "%." + cfg.digits() + "f",
                    Math.round(v * f) / f
            );
        }
    }

    static class BatchStats {
        int applied = 0;
        int skipped = 0;
        double totalAbsAmount = 0;

        void recordApplied(double amt) {
            applied++;
            totalAbsAmount += Math.abs(amt);
        }

        void recordSkipped() {
            skipped++;
        }
    }

    static class Report {
        private final StringBuilder out = new StringBuilder();

        void line(String s) { out.append(s).append("\n"); }
        void blank() { out.append("\n"); }

        @Override
        public String toString() {
            return out.toString();
        }
    }

    /* =======================
       Batch Processor
       ======================= */

    public static String processDailyBatch(
            List<BankAccount> accounts,
            List<Txn> txns,
            PolicyConfig policy,
            FormatConfig fmtCfg
    ) {
        MoneyFormatter fmt = new MoneyFormatter(fmtCfg);
        Report r = new Report();
        BatchStats stats = new BatchStats();

        Map<String, BankAccount> accountIndex =
                accounts.stream().collect(Collectors.toMap(BankAccount::id, a -> a));

        r.line("=== BANK BATCH REPORT ===");
        r.blank();
        r.line("-- APPLY --");

        for (Txn t : txns) {
            if (!policy.includeZeroAmountTxns() && t.amount == 0.0)
                continue;

            BankAccount acct = accountIndex.get(t.accountId);
            if (acct == null) {
                stats.recordSkipped();
                if (fmtCfg.debug())
                    r.line("[dbg] unknown " + t.accountId);
                continue;
            }

            r.line(t.type + " acct=" + acct.id()
                    + " owner=" + acct.owner()
                    + " amt=" + fmt.format(t.amount)
                    + " " + fmtCfg.currency()
                    + " memo=" + t.memo);

            boolean applied = applyTransaction(acct, t);

            if (Math.abs(t.amount) >= policy.largeTxnThreshold()) {
                acct.flag();
                r.line("  ** FLAG large txn **");
            }

            if (applied) {
                stats.recordApplied(t.amount);
                r.line("  newBal=" + fmt.format(acct.balance()));
            } else {
                stats.recordSkipped();
                r.line("  DECLINED");
            }

            if (acct.balance() >= policy.vipBalanceThreshold())
                r.line("  VIP NOTE");

            r.blank();
        }

        r.line("-- POST-CHECKS --");
        for (BankAccount a : accounts) {
            if (a.invalidBalance()) {
                a.flag();
                r.line("Flag " + a.id() + " invalid balance");
            }
        }

        r.blank();
        r.line("-- SUMMARY A --");
        for (BankAccount a : accounts) {
            r.line(a.id() + " " + a.type() + " " + a.owner()
                    + " bal=" + fmt.format(a.balance())
                    + (a.flagged() ? " [FLAG]" : ""));
        }

        r.blank();
        r.line("-- TOTALS --");
        r.line("applied=" + stats.applied
                + " skipped=" + stats.skipped
                + " absTotal=" + fmt.format(stats.totalAbsAmount)
                + " " + fmtCfg.currency());

        r.blank();
        r.line("-- SUMMARY B --");
        ListIterator<BankAccount> it = accounts.listIterator(accounts.size());
        while (it.hasPrevious()) {
            BankAccount a = it.previous();
            r.line("[" + a.type() + "] " + a.owner()
                    + " id=" + a.id()
                    + " bal=" + fmt.format(a.balance())
                    + (a.flagged() ? " *" : ""));
        }

        return r.toString();
    }

    private static boolean applyTransaction(BankAccount acct, Txn t) {
        return switch (t.type) {
            case DEPOSIT -> {
                acct.deposit(t.amount);
                yield true;
            }
            case WITHDRAW -> acct.withdraw(t.amount);
        };
    }

    /* =======================
       MAIN (UNCHANGED)
       ======================= */

    public static void main(String[] args) {
        List<BankAccount> accounts = new ArrayList<>();
        accounts.add(new CheckingAccount("C-100", "A. Chen", 250, 100));
        accounts.add(new SavingsAccount("S-200", "B. Patel", 1200, 0.02));
        accounts.add(new CheckingAccount("C-300", "C. Rivera", 40, 50));
        accounts.add(new SavingsAccount("S-400", "D. Smith", 9000, 0.03));

        List<Txn> txns = List.of(
                new Txn("C-100", TxnType.WITHDRAW, 75, "ATM withdrawal"),
                new Txn("C-300", TxnType.WITHDRAW, 120, "Billpay overdraft test"),
                new Txn("S-200", TxnType.WITHDRAW, 1300, "Savings overdraft test"),
                new Txn("S-400", TxnType.DEPOSIT, 1500, "Bonus deposit"),
                new Txn("C-100", TxnType.DEPOSIT, 25, "Cash deposit")
        );

        PolicyConfig policy = new PolicyConfig(false, 1000.0, 5000.0);
        FormatConfig format = new FormatConfig(true, "USD", 2, true);

        System.out.println(processDailyBatch(accounts, txns, policy, format));
    }
}
