package edu.kettering.refactoring.bank;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class SmellyBankHomeworkShorterTest {

    /* -----------------------
       Helpers
       ----------------------- */

    private List<SmellyBankHomeworkShorter.BankAccount> baseAccounts() {
        List<SmellyBankHomeworkShorter.BankAccount> accounts = new ArrayList<>();

        accounts.add(
            new SmellyBankHomeworkShorter.CheckingAccount(
                "C-100",
                "Sam",
                250.0,
                200.0
            )
        );

        accounts.add(
            new SmellyBankHomeworkShorter.SavingsAccount(
                "S-200",
                "Alex",
                1200.0,
                0.02
            )
        );

        return accounts;
    }

    private SmellyBankHomeworkShorter.PolicyConfig policy() {
        return new SmellyBankHomeworkShorter.PolicyConfig(
                false,
                1000.0,
                5000.0
        );
    }

    private SmellyBankHomeworkShorter.FormatConfig format() {
        return new SmellyBankHomeworkShorter.FormatConfig(
                false,
                "USD",
                2,
                true
        );
    }

    /* -----------------------
       Tests
       ----------------------- */

    @Test
    void deposit_shouldIncreaseBalance() {
        var accounts = baseAccounts();

        List<SmellyBankHomeworkShorter.Txn> txns = List.of(
            new SmellyBankHomeworkShorter.Txn(
                "C-100",
                SmellyBankHomeworkShorter.TxnType.DEPOSIT,
                50.0,
                "cash"
            )
        );

        SmellyBankHomeworkShorter.processDailyBatch(
                accounts, txns, policy(), format()
        );

        assertEquals(300.0, accounts.get(0).balance(), 1e-9);
    }

    @Test
    void withdrawal_shouldDecreaseBalance() {
        var accounts = baseAccounts();

        List<SmellyBankHomeworkShorter.Txn> txns = List.of(
            new SmellyBankHomeworkShorter.Txn(
                "C-100",
                SmellyBankHomeworkShorter.TxnType.WITHDRAW,
                100.0,
                "atm"
            )
        );

        SmellyBankHomeworkShorter.processDailyBatch(
                accounts, txns, policy(), format()
        );

        assertEquals(150.0, accounts.get(0).balance(), 1e-9);
    }

    @Test
    void overdraftBeyondLimit_shouldFlagAccount() {
        var accounts = baseAccounts();
        var acct = accounts.get(0);

        List<SmellyBankHomeworkShorter.Txn> txns = List.of(
            new SmellyBankHomeworkShorter.Txn(
                "C-100",
                SmellyBankHomeworkShorter.TxnType.WITHDRAW,
                600.0,
                "rent"
            )
        );

        SmellyBankHomeworkShorter.processDailyBatch(
                accounts, txns, policy(), format()
        );

        assertTrue(acct.flagged());
    }

    @Test
    void savingsWithdrawalBelowZero_shouldBeRejectedAndFlagged() {
        var accounts = baseAccounts();
        var acct = accounts.get(1);

        List<SmellyBankHomeworkShorter.Txn> txns = List.of(
            new SmellyBankHomeworkShorter.Txn(
                "S-200",
                SmellyBankHomeworkShorter.TxnType.WITHDRAW,
                2000.0,
                "oops"
            )
        );

        SmellyBankHomeworkShorter.processDailyBatch(
                accounts, txns, policy(), format()
        );

        assertTrue(acct.flagged());
        assertEquals(1200.0, acct.balance(), 1e-9);
    }

    @Test
    void processDailyBatch_returnsFormattedReport() {
        var accounts = baseAccounts();

        String report = SmellyBankHomeworkShorter.processDailyBatch(
                accounts,
                List.of(),
                policy(),
                format()
        );

        assertNotNull(report);
        assertTrue(report.contains("BANK BATCH REPORT"));
    }
}
