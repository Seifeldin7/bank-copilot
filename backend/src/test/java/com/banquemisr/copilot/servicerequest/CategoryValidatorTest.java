package com.banquemisr.copilot.servicerequest;

import static org.assertj.core.api.Assertions.assertThat;

import com.banquemisr.copilot.servicerequest.RequestPayload.AccountService;
import com.banquemisr.copilot.servicerequest.RequestPayload.AccountServiceType;
import com.banquemisr.copilot.servicerequest.RequestPayload.AccountType;
import com.banquemisr.copilot.servicerequest.RequestPayload.AdditionalDocumentation;
import com.banquemisr.copilot.servicerequest.RequestPayload.CardIssue;
import com.banquemisr.copilot.servicerequest.RequestPayload.CardService;
import com.banquemisr.copilot.servicerequest.RequestPayload.DigitalBanking;
import com.banquemisr.copilot.servicerequest.RequestPayload.DigitalChannel;
import com.banquemisr.copilot.servicerequest.RequestPayload.DigitalIssue;
import com.banquemisr.copilot.servicerequest.ValidationResult.Issue;
import com.banquemisr.copilot.servicerequest.ValidationResult.Severity;
import java.util.List;
import org.junit.jupiter.api.Test;

class CategoryValidatorTest {

    private final CategoryValidator validator = RequestFixtures.validator();

    @Test
    void aCompleteCardRequestIsValid() {
        ValidationResult result = validate(RequestFixtures.card());

        assertThat(result.valid()).isTrue();
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void reportsEveryBrokenCommonAndCardRule() {
        ValidationResult result = validator.validate("Hi", "Short", new CardService("12345", "48", null, "cairo", null));

        assertThat(result.valid()).isFalse();
        assertThat(errorFields(result)).containsExactly("title", "description", "payload.customerReference",
                "payload.cardLast4", "payload.issueType", "payload.deliveryBranch");
    }

    @Test
    void aStolenCardWithoutAPoliceReportOnlyWarns() {
        ValidationResult result = validate(new CardService("CUS-104233", "4821", CardIssue.STOLEN, "CAI-012", "Lost at the mall"));

        assertThat(result.valid()).isTrue();
        assertThat(result.issues()).extracting(Issue::field).containsExactly("payload.notes", "payload.issueType");
        assertThat(result.issues()).extracting(Issue::severity).containsOnly(Severity.WARNING);
    }

    @Test
    void deviceChangeIsOnlyForTheMobileApp() {
        ValidationResult result = validate(new DigitalBanking("CUS-104233", DigitalChannel.INTERNET_BANKING,
                DigitalIssue.DEVICE_CHANGE, "01012345678", null));

        assertThat(errorFields(result)).containsExactly("payload.issueType");
    }

    @Test
    void theRegisteredMobileMustBeAnEgyptianNumber() {
        ValidationResult result = validate(new DigitalBanking("CUS-104233", DigitalChannel.MOBILE_APP,
                DigitalIssue.PASSWORD_RESET, "01312345678", null));

        assertThat(errorFields(result)).containsExactly("payload.registeredMobile");
    }

    @Test
    void chequeBooksNeedACurrentAccount() {
        ValidationResult result = validate(new AccountService("CUS-104233", "7781", AccountType.SAVINGS,
                AccountServiceType.CHEQUE_BOOK, null, null, null));

        assertThat(errorFields(result)).containsExactly("payload.accountType");
    }

    @Test
    void statementsNeedAnOrderedPeriodAndWarnPastTheConfiguredLimit() {
        assertThat(errorFields(validate(statement(null, "2026-05-01")))).containsExactly("payload.periodFrom");
        assertThat(errorFields(validate(statement("2026-05-01", "2026-01-01")))).containsExactly("payload.periodTo");

        ValidationResult longPeriod = validate(statement("2024-01-01", "2026-01-01"));
        assertThat(longPeriod.valid()).isTrue();
        assertThat(longPeriod.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.severity()).isEqualTo(Severity.WARNING);
            assertThat(issue.message()).contains("12 months");
        });
    }

    @Test
    void documentDueDatesCannotBePastAndWarnAfterTheConfiguredDays() {
        assertThat(errorFields(validate(documents(List.of("Proof of address"), "2026-06-14")))).containsExactly("payload.dueDate");
        assertThat(errorFields(validate(documents(List.of(" "), "2026-06-20")))).containsExactly("payload.requiredDocuments");

        ValidationResult late = validate(documents(List.of("Proof of address"), "2026-07-15"));
        assertThat(late.valid()).isTrue();
        assertThat(late.issues()).singleElement().extracting(Issue::severity).isEqualTo(Severity.WARNING);
    }

    private ValidationResult validate(RequestPayload payload) {
        return validator.validate("A clear request title", "A description that is long enough.", payload);
    }

    private static AccountService statement(String from, String to) {
        return new AccountService("CUS-104233", "7781", AccountType.CURRENT, AccountServiceType.STATEMENT, from, to, null);
    }

    private static AdditionalDocumentation documents(List<String> documents, String dueDate) {
        return new AdditionalDocumentation("CUS-104233", null, documents, dueDate, null);
    }

    private static List<String> errorFields(ValidationResult result) {
        return result.issues().stream().filter(issue -> issue.severity() == Severity.ERROR).map(Issue::field).toList();
    }
}
