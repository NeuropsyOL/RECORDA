package de.uol.neuropsy.recorda.recorder;

public enum QualityState {

    OK("good"),
    NOT_RESPONDING("not responding"),
    LAGGY("lagging");

    public final String displayName;
    QualityState(String displayName) {
        this.displayName = displayName;
    }
}
