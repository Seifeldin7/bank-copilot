package com.banquemisr.copilot.approval;

import com.banquemisr.copilot.approval.ApprovalDtos.ApproveRequest;
import com.banquemisr.copilot.approval.ApprovalDtos.EditApproveRequest;
import com.banquemisr.copilot.approval.ApprovalDtos.ExecuteResponse;
import com.banquemisr.copilot.approval.ApprovalDtos.RejectRequest;
import com.banquemisr.copilot.identity.CurrentUser;
import com.banquemisr.copilot.servicerequest.RequestDtos.RequestView;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/approvals")
@PreAuthorize("hasRole('SUPERVISOR')")
public class ApprovalController {

    private final ApprovalService approvals;
    private final CurrentUser currentUser;

    public ApprovalController(ApprovalService approvals, CurrentUser currentUser) {
        this.approvals = approvals;
        this.currentUser = currentUser;
    }

    @GetMapping("/queue")
    public List<RequestView> queue() {
        return approvals.queue();
    }

    @PostMapping("/{id}/approve")
    public RequestView approve(@PathVariable UUID id, @Valid @RequestBody ApproveRequest body, @AuthenticationPrincipal Jwt jwt) {
        return approvals.approve(currentUser.from(jwt), id, body);
    }

    @PostMapping("/{id}/reject")
    public RequestView reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest body, @AuthenticationPrincipal Jwt jwt) {
        return approvals.reject(currentUser.from(jwt), id, body);
    }

    @PostMapping("/{id}/edit-approve")
    public RequestView editAndApprove(@PathVariable UUID id, @Valid @RequestBody EditApproveRequest body,
                                      @AuthenticationPrincipal Jwt jwt) {
        return approvals.editAndApprove(currentUser.from(jwt), id, body);
    }

    @PostMapping("/{id}/execute")
    public ExecuteResponse execute(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return approvals.execute(currentUser.from(jwt), id);
    }
}
