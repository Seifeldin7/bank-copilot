package com.banquemisr.copilot.approval;

import com.banquemisr.copilot.servicerequest.RequestPayload;
import com.banquemisr.copilot.servicerequest.RequestPayload.AccountService;
import com.banquemisr.copilot.servicerequest.RequestPayload.AdditionalDocumentation;
import com.banquemisr.copilot.servicerequest.RequestPayload.CardIssue;
import com.banquemisr.copilot.servicerequest.RequestPayload.CardService;
import com.banquemisr.copilot.servicerequest.RequestPayload.DigitalBanking;
import com.banquemisr.copilot.servicerequest.ServiceRequest;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

public final class SimulatedActions {

    public record Action(String description, Map<String, String> result) {
    }

    private SimulatedActions() {
    }

    public static Action run(ServiceRequest request, LocalDate today) {
        String number = request.getReference().substring(request.getReference().length() - 6);
        RequestPayload payload = request.getPayload();
        return switch (payload) {
            case CardService card -> new Action(
                    card.issueType() == CardIssue.LIMIT_INQUIRY ? "Simulated card limit inquiry logged" : "Simulated replacement card order placed",
                    Map.of("cardOrderRef", "CO-" + number,
                            "collectionBranch", card.deliveryBranch(),
                            "expectedReadyInWorkingDays", card.issueType() == CardIssue.LOST || card.issueType() == CardIssue.STOLEN ? "7" : "5"));
            case DigitalBanking digital -> new Action(
                    "Simulated " + words(digital.issueType().name()) + " on " + words(digital.channel().name()),
                    Map.of("ticketRef", "DB-" + number, "smsSentTo", mask(digital.registeredMobile())));
            case AccountService account -> new Action(
                    "Simulated " + words(account.serviceType().name()) + " for account ending " + account.accountLast4(),
                    Map.of("serviceRef", "AS-" + number, "readyBy", today.plusDays(1).toString()));
            case AdditionalDocumentation documents -> new Action(
                    "Simulated documentation reminder scheduled",
                    Map.of("reminderRef", "RM-" + number,
                            "documents", String.valueOf(documents.requiredDocuments().size()),
                            "dueDate", documents.dueDate()));
        };
    }

    private static String words(String enumName) {
        return enumName.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String mask(String mobile) {
        return mobile.substring(0, 3) + "*****" + mobile.substring(mobile.length() - 3);
    }
}
