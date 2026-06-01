package com.lumoxu.cof.common.catalog;

public final class ReviewStatus {

    public static final String PENDING = "pending";
    public static final String APPROVED = "approved";
    public static final String REJECTED = "rejected";

    private ReviewStatus() {
    }

    public static boolean isApproved(String status) {
        return APPROVED.equalsIgnoreCase(status);
    }

    public static boolean isVisibleToUser(String status, String submitterId, String viewerId) {
        if (isApproved(status)) {
            return true;
        }
        return viewerId != null && viewerId.equals(submitterId);
    }
}
