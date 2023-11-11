package de.uol.neuropsy.LSLReceiver.recorder;

public interface StreamQualityListener {
    void streamQualityChanged(QualityState qualityNow);
}
