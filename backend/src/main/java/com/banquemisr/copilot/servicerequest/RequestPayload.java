package com.banquemisr.copilot.servicerequest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.constraints.Size;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "category")
@JsonSubTypes({
        @JsonSubTypes.Type(value = RequestPayload.CardService.class, name = "CARD_SERVICE"),
        @JsonSubTypes.Type(value = RequestPayload.DigitalBanking.class, name = "DIGITAL_BANKING"),
        @JsonSubTypes.Type(value = RequestPayload.AccountService.class, name = "ACCOUNT_SERVICE"),
        @JsonSubTypes.Type(value = RequestPayload.AdditionalDocumentation.class, name = "ADDITIONAL_DOCUMENTATION")
})
public sealed interface RequestPayload {

    RequestCategory category();

    String customerReference();

    enum CardIssue { LOST, STOLEN, REPLACEMENT, LIMIT_INQUIRY }

    enum DigitalChannel { INTERNET_BANKING, MOBILE_APP }

    enum DigitalIssue { ENROLLMENT, PASSWORD_RESET, DEVICE_CHANGE, TRANSACTION_ISSUE }

    enum AccountType { CURRENT, SAVINGS, FOREIGN_CURRENCY }

    enum AccountServiceType { STATEMENT, BALANCE_CERTIFICATE, DORMANCY_REACTIVATION, CHEQUE_BOOK }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CardService(
            @Size(max = 40) String customerReference,
            @Size(max = 40) String cardLast4,
            CardIssue issueType,
            @Size(max = 40) String deliveryBranch,
            @Size(max = 500) String notes) implements RequestPayload {

        public RequestCategory category() {
            return RequestCategory.CARD_SERVICE;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DigitalBanking(
            @Size(max = 40) String customerReference,
            DigitalChannel channel,
            DigitalIssue issueType,
            @Size(max = 40) String registeredMobile,
            @Size(max = 500) String notes) implements RequestPayload {

        public RequestCategory category() {
            return RequestCategory.DIGITAL_BANKING;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AccountService(
            @Size(max = 40) String customerReference,
            @Size(max = 40) String accountLast4,
            AccountType accountType,
            AccountServiceType serviceType,
            @Size(max = 40) String periodFrom,
            @Size(max = 40) String periodTo,
            @Size(max = 500) String notes) implements RequestPayload {

        public RequestCategory category() {
            return RequestCategory.ACCOUNT_SERVICE;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AdditionalDocumentation(
            @Size(max = 40) String customerReference,
            @Size(max = 40) String relatedReference,
            @Size(max = 10) List<@Size(max = 200) String> requiredDocuments,
            @Size(max = 40) String dueDate,
            @Size(max = 500) String notes) implements RequestPayload {

        public RequestCategory category() {
            return RequestCategory.ADDITIONAL_DOCUMENTATION;
        }
    }
}
