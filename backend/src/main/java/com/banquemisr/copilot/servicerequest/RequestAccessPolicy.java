package com.banquemisr.copilot.servicerequest;

import com.banquemisr.copilot.common.ApiException;
import com.banquemisr.copilot.identity.AppUser;

public final class RequestAccessPolicy {

    private RequestAccessPolicy() {
    }

    public static ApiException notFoundError() {
        return ApiException.notFound("Service request not found.");
    }

    public static void requireVisible(AppUser user, ServiceRequest request) {
        if (!user.isSupervisor() && !request.isOwnedBy(user)) {
            throw notFoundError();
        }
    }

    public static void requireOwner(AppUser user, ServiceRequest request) {
        requireVisible(user, request);
        if (!request.isOwnedBy(user)) {
            throw ApiException.forbidden("Only the employee who created this request can change it.");
        }
    }

    public static void requireCanDecide(AppUser user, ServiceRequest request) {
        if (!user.isSupervisor()) {
            throw ApiException.forbidden("This action requires the SUPERVISOR role.");
        }
        if (request.isOwnedBy(user)) {
            throw ApiException.forbidden("Supervisors cannot approve, reject or edit their own requests.");
        }
    }
}
