package edu.kettering.refactoring.bank;

import java.util.*;
import java.util.stream.Collectors;

public class SmellyBankHomeworkShorter {

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
        private final double rate;

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

    static class BatchStats {
        int applied = 0;
        int skipped = 0;
        double absTotal = 0;

        void applied(double amt) {
            applied++;
            absTotal += Math.abs(amt);
        }

        void skipped() {
            skipped++;
        }
    }

    public static BankAccount createCheckingAccount(String id, String owner, double balance, double overdraft) {
        return new CheckingAccount(id, owner, balance, overdraft);
    }

    public static BankAccount createSavingsAccount(String id, String owner, double balance, double rate) {
        return new SavingsAccount(id, owner, balance, rate);
    }

    public static String processDailyBatch(
            List<BankAccount> accounts,
            List<Txn> txns,
            PolicyConfig policy,
            FormatConfig fmt) {
        StringBuilder out = new StringBuilder();
        out.append("=== BANK BATCH REPORT ===\n");

        Map<String, BankAccount> accountIndex = accounts.stream()
                .collect(Collectors.toMap(BankAccount::id, a -> a));

        List<Txn> filteredTxns = txns.stream()
                .filter(t -> policy.includeZeroAmountTxns || t.amount != 0.0)
                .toList();

        BatchStats stats = new BatchStats();

        out.append("\n-- APPLY --\n");
        for (Txn t : filteredTxns) {
            BankAccount acct = accountIndex.get(t.accountId);

            if (acct == null) {
                stats.skipped();
                if (fmt.debug())
                    out.append("[dbg] unknown ").append(t.accountId).append("\n");
                continue;
            }

            out.append(t.type).append(" acct=").append(acct.id())
                    .append(" owner=").append(acct.owner())
                    .append(" amt=").append(format(t.amount, fmt))
                    .append(" ").append(fmt.currency())
                    .append(" memo=").append(t.memo)
                    .append("\n");

            boolean applied = false;

            if (t.type == TxnType.DEPOSIT) {
                acct.deposit(t.amount);
                applied = true;
            } else if (t.type == TxnType.WITHDRAW) {
                applied = acct.canWithdraw(t.amount);
                if (applied)
                    acct.withdraw(t.amount);
            }

            if (Math.abs(t.amount) >= policy.largeTxnThreshold()) {
                acct.flag();
                out.append("  ** FLAG large txn **\n");
            }

            if (applied) {
                stats.applied(t.amount);
                out.append("  newBal=").append(format(acct.balance(), fmt)).append("\n");
            } else {
                stats.skipped();
                out.append("  DECLINED\n");
            }

            if (acct.balance() >= policy.vipBalanceThreshold()) {
                out.append("  VIP NOTE\n");
            }

            out.append("\n");
        }

        out.append("-- POST-CHECKS --\n");
        for (BankAccount a : accounts) {
            if (a.invalidBalance()) {
                a.flag();
                out.append("Flag ").append(a.id()).append(" invalid balance\n");
            }
        }

        out.append("\n-- SUMMARY A --\n");
        for (BankAccount a : accounts) {
            out.append(a.id()).append(" ").append(a.type())
                    .append(" ").append(a.owner())
                    .append(" bal=").append(format(a.balance(), fmt))
                    .append(a.flagged() ? " [FLAG]" : "")
                    .append("\n");
        }

        out.append("\n-- TOTALS --\n");
        out.append("applied=").append(stats.applied)
                .append(" skipped=").append(stats.skipped)
                .append(" absTotal=").append(format(stats.absTotal, fmt))
                .append(" ").append(fmt.currency())
                .append("\n");

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

        return out.toString();
    }

    private static String format(double value, FormatConfig fmt) {
        if (!fmt.rounding())
            return Double.toString(value);
        double factor = Math.pow(10, fmt.digits());
        double rounded = Math.round(value * factor) / factor;
        return String.format(Locale.US, "%." + fmt.digits() + "f", rounded);
    }

    public static void main(String[] args) {
        List<BankAccount> accounts = new ArrayList<>();
        accounts.add(createCheckingAccount("C-100", "A. Chen", 250, 100));
        accounts.add(createSavingsAccount("S-200", "B. Patel", 1200, 0.02));
        accounts.add(createCheckingAccount("C-300", "C. Rivera", 40, 50));
        accounts.add(createSavingsAccount("S-400", "D. Smith", 9000, 0.03));

        List<Txn> txns = List.of(
                new Txn("C-100", TxnType.WITHDRAW, 75, "ATM withdrawal"),
                new Txn("C-300", TxnType.WITHDRAW, 120, "Billpay overdraft test"),
                new Txn("S-200", TxnType.WITHDRAW, 1300, "Savings overdraft test"),
                new Txn("S-400", TxnType.DEPOSIT, 1500, "Bonus deposit"),
                new Txn("C-100", TxnType.DEPOSIT, 25, "Cash deposit"));

        PolicyConfig policy = new PolicyConfig(false, 1000.0, 5000.0);
        FormatConfig format = new FormatConfig(true, "USD", 2, true);

        System.out.println(processDailyBatch(accounts, txns, policy, format));
    }
}
