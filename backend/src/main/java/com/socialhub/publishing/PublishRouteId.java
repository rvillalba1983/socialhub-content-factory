package com.socialhub.publishing;

/** Las vías por las que SocialHub puede publicar, por orden de preferencia. */
public enum PublishRouteId {
    OFFICIAL("API oficial"), PUBLER("Publer"), PUBLORA("Publora");

    private final String label;

    PublishRouteId(String label) { this.label = label; }

    public String label() { return label; }
}
