package de.uol.neuropsy.recorda.recorder;

public interface StreamQualityListener {
    void streamQualityChanged(int streamNumber, QualityMetrics qualityNow);
}
