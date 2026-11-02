package com.banquemisr.copilot.assistant.agent;

import com.banquemisr.copilot.servicerequest.RequestCategory;
import com.banquemisr.copilot.servicerequest.RequestPayload;
import com.banquemisr.copilot.servicerequest.RequestPayload.AccountService;
import com.banquemisr.copilot.servicerequest.RequestPayload.AccountServiceType;
import com.banquemisr.copilot.servicerequest.RequestPayload.AccountType;
import com.banquemisr.copilot.servicerequest.RequestPayload.AdditionalDocumentation;
import com.banquemisr.copilot.servicerequest.RequestPayload.CardIssue;
import com.banquemisr.copilot.servicerequest.RequestPayload.CardService;
import com.banquemisr.copilot.servicerequest.RequestPayload.DigitalBanking;
import com.banquemisr.copilot.servicerequest.RequestPayload.DigitalChannel;
import com.banquemisr.copilot.servicerequest.RequestPayload.DigitalIssue;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class CategorySchemaCatalog {

    private static final Map<String, RequestCategory> BY_DOCUMENT_CATEGORY = Map.of(
            "Cards", RequestCategory.CARD_SERVICE,
            "Digital Banking", RequestCategory.DIGITAL_BANKING,
            "Accounts", RequestCategory.ACCOUNT_SERVICE,
            "Compliance", RequestCategory.ADDITIONAL_DOCUMENTATION);

    public Optional<RequestCategory> forDocumentCategory(String documentCategory) {
        return Optional.ofNullable(BY_DOCUMENT_CATEGORY.get(documentCategory));
    }

    public String describe(RequestCategory category) {
        String common = "customerReference: string like CUS-123456\n";
        String specific = switch (category) {
            case CARD_SERVICE -> "cardLast4: exactly 4 digits\n"
                    + "issueType: one of " + values(CardIssue.values()) + "\n"
                    + "deliveryBranch: branch code like CAI-012\n";
            case DIGITAL_BANKING -> "channel: one of " + values(DigitalChannel.values()) + "\n"
                    + "issueType: one of " + values(DigitalIssue.values()) + "\n"
                    + "registeredMobile: 11 digits starting with 010, 011, 012 or 015\n";
            case ACCOUNT_SERVICE -> "accountLast4: exactly 4 digits\n"
                    + "accountType: one of " + values(AccountType.values()) + "\n"
                    + "serviceType: one of " + values(AccountServiceType.values()) + "\n"
                    + "periodFrom, periodTo: dates as YYYY-MM-DD, only for STATEMENT\n";
            case ADDITIONAL_DOCUMENTATION -> "relatedReference: optional request reference like SR-2026-000123\n"
                    + "requiredDocuments: list of document names\n"
                    + "dueDate: date as YYYY-MM-DD\n";
        };
        return common + specific + "notes: optional free text\n";
    }

    public RequestPayload empty(RequestCategory category, String branchCode) {
        return switch (category) {
            case CARD_SERVICE -> new CardService("", "", CardIssue.REPLACEMENT, branchCode, "");
            case DIGITAL_BANKING -> new DigitalBanking("", DigitalChannel.MOBILE_APP, DigitalIssue.PASSWORD_RESET, "", "");
            case ACCOUNT_SERVICE -> new AccountService("", "", AccountType.CURRENT, AccountServiceType.STATEMENT, "", "", "");
            case ADDITIONAL_DOCUMENTATION -> new AdditionalDocumentation("", "", List.of(), "", "");
        };
    }

    private static String values(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.joining(", "));
    }
}
