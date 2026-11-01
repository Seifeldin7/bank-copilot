package com.banquemisr.copilot.servicerequest;

import com.banquemisr.copilot.common.CopilotProperties;
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
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class CategoryValidator {

    private static final Pattern CUSTOMER_REFERENCE = Pattern.compile("CUS-\\d{6}");
    private static final Pattern LAST_4 = Pattern.compile("\\d{4}");
    private static final Pattern BRANCH_CODE = Pattern.compile("[A-Z]{3}-\\d{3}");
    private static final Pattern EGYPTIAN_MOBILE = Pattern.compile("01[0125]\\d{8}");

    private final Clock clock;
    private final CopilotProperties.Requests limits;

    public CategoryValidator(Clock clock, CopilotProperties properties) {
        this.clock = clock;
        this.limits = properties.requests();
    }

    public ValidationResult validate(String title, String description, RequestPayload payload) {
        List<Issue> issues = new ArrayList<>();
        if (blank(title) || title.trim().length() < 5) {
            error(issues, "title", "Title must be at least 5 characters.");
        }
        if (blank(description) || description.trim().length() < 10) {
            error(issues, "description", "Description must be at least 10 characters.");
        }
        if (!matches(CUSTOMER_REFERENCE, payload.customerReference())) {
            error(issues, "payload.customerReference", "Customer reference must look like CUS-123456.");
        }

        switch (payload) {
            case CardService card -> checkCard(card, issues);
            case DigitalBanking digital -> checkDigital(digital, issues);
            case AccountService account -> checkAccount(account, issues);
            case AdditionalDocumentation documents -> checkDocuments(documents, issues);
        }

        boolean valid = issues.stream().noneMatch(issue -> issue.severity() == Severity.ERROR);
        return new ValidationResult(valid, List.copyOf(issues), clock.instant().toString());
    }

    private void checkCard(CardService card, List<Issue> issues) {
        if (!matches(LAST_4, card.cardLast4())) {
            error(issues, "payload.cardLast4", "Enter exactly the last 4 digits of the card.");
        }
        if (card.issueType() == null) {
            error(issues, "payload.issueType", "Select the card issue type.");
        }
        if (!matches(BRANCH_CODE, card.deliveryBranch())) {
            error(issues, "payload.deliveryBranch", "Delivery branch must look like CAI-012.");
        }
        if (card.issueType() == CardIssue.STOLEN && (card.notes() == null || !card.notes().toLowerCase(Locale.ROOT).contains("police"))) {
            warning(issues, "payload.notes", "Stolen cards with disputed transactions need a police report reference within 48 hours.");
        }
        if (card.issueType() == CardIssue.LOST || card.issueType() == CardIssue.STOLEN) {
            warning(issues, "payload.issueType", "Confirm the card was blocked before requesting a replacement.");
        }
    }

    private void checkDigital(DigitalBanking digital, List<Issue> issues) {
        if (digital.channel() == null) {
            error(issues, "payload.channel", "Select the digital channel.");
        }
        if (digital.issueType() == null) {
            error(issues, "payload.issueType", "Select the issue type.");
        }
        if (!matches(EGYPTIAN_MOBILE, digital.registeredMobile())) {
            error(issues, "payload.registeredMobile", "Registered mobile must be an 11-digit number starting with 010, 011, 012 or 015.");
        }
        if (digital.channel() == DigitalChannel.INTERNET_BANKING && digital.issueType() == DigitalIssue.DEVICE_CHANGE) {
            error(issues, "payload.issueType", "Device change applies to the mobile app channel only.");
        }
    }

    private void checkAccount(AccountService account, List<Issue> issues) {
        if (!matches(LAST_4, account.accountLast4())) {
            error(issues, "payload.accountLast4", "Enter exactly the last 4 digits of the account.");
        }
        if (account.accountType() == null) {
            error(issues, "payload.accountType", "Select the account type.");
        }
        if (account.serviceType() == null) {
            error(issues, "payload.serviceType", "Select the service type.");
        }
        if (account.serviceType() == AccountServiceType.CHEQUE_BOOK && account.accountType() != AccountType.CURRENT) {
            error(issues, "payload.accountType", "Cheque books can only be issued for current accounts.");
        }
        if (account.serviceType() != AccountServiceType.STATEMENT) {
            return;
        }
        LocalDate from = parseDate(account.periodFrom());
        LocalDate to = parseDate(account.periodTo());
        if (from == null || to == null) {
            error(issues, "payload.periodFrom", "Statement requests need a start and end date.");
        } else if (to.isBefore(from)) {
            error(issues, "payload.periodTo", "The end date must be after the start date.");
        } else if (ChronoUnit.MONTHS.between(from.withDayOfMonth(1), to.withDayOfMonth(1)) > limits.statementMaxMonths()) {
            warning(issues, "payload.periodTo", "Statements are limited to " + limits.statementMaxMonths()
                    + " months per request; the supervisor may need to split or shorten the period.");
        }
    }

    private void checkDocuments(AdditionalDocumentation documents, List<Issue> issues) {
        boolean noDocuments = documents.requiredDocuments() == null
                || documents.requiredDocuments().stream().allMatch(CategoryValidator::blank);
        if (noDocuments) {
            error(issues, "payload.requiredDocuments", "List at least one missing document.");
        }
        LocalDate due = parseDate(documents.dueDate());
        if (due == null) {
            error(issues, "payload.dueDate", "Set a due date for the documents.");
            return;
        }
        long days = ChronoUnit.DAYS.between(LocalDate.now(clock), due);
        if (days < 0) {
            error(issues, "payload.dueDate", "The due date cannot be in the past.");
        } else if (days > limits.documentsDueMaxDays()) {
            warning(issues, "payload.dueDate", "Policy requires missing documents within " + limits.documentsDueMaxDays()
                    + " days of the visit.");
        }
    }

    private static LocalDate parseDate(String value) {
        if (blank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static boolean matches(Pattern pattern, String value) {
        return value != null && pattern.matcher(value.trim()).matches();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static void error(List<Issue> issues, String field, String message) {
        issues.add(new Issue(field, message, Severity.ERROR));
    }

    private static void warning(List<Issue> issues, String field, String message) {
        issues.add(new Issue(field, message, Severity.WARNING));
    }
}
