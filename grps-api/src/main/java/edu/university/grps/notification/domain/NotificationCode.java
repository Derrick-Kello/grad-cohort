package edu.university.grps.notification.domain;

/** Notification catalogue (PRD section 13). Urgent codes always email immediately, ignoring digest preference. */
public enum NotificationCode {
    N01("Milestone available", false),
    N02("Presentation scheduled", false),
    N03("Presentation rescheduled or cancelled", false),
    N04("Presentation reminder", false),
    N05("Outcome recorded", false),
    N06("Evidence uploaded", false),
    N07("Milestone approved", false),
    N08("Milestone returned", true),
    N09("Publication submitted", false),
    N10("Publication reviewed", false),
    N11("Thesis format check", false),
    N12("Thesis screening", false),
    N13("Thesis accepted", false),
    N14("Attendance warning", true),
    N15("Deadline warning", true),
    N16("Status at risk", true),
    N17("Flagged for expulsion", true),
    N18("Expulsion decision", true),
    N19("Supervisor change", false),
    N20("Supervisor comment", false),
    ANN("Announcement", false),
    SYS("System", false);

    private final String label;
    private final boolean urgent;

    NotificationCode(String label, boolean urgent) {
        this.label = label;
        this.urgent = urgent;
    }

    public String label() {
        return label;
    }

    public boolean urgent() {
        return urgent;
    }

    /** The catalogue code as written in the PRD, e.g. "N-07". */
    public String display() {
        return name().startsWith("N") && name().length() == 3 ? "N-" + name().substring(1) : name();
    }
}
