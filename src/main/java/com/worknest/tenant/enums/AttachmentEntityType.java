package com.worknest.tenant.enums;

public enum AttachmentEntityType {
    TASK,
    PROJECT,
    ANNOUNCEMENT,
    LEAVE,
    LEAVE_REQUEST;

    public AttachmentEntityType canonical() {
        return this == LEAVE ? LEAVE_REQUEST : this;
    }

    public boolean isLeaveType() {
        return this == LEAVE || this == LEAVE_REQUEST;
    }
}
