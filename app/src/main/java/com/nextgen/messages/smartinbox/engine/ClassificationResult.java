package com.nextgen.messages.smartinbox.engine;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Result model emitted by the classification pipeline.
 */
public class ClassificationResult {

    @NonNull
    private final String categoryKey;
    private final float confidence;
    @NonNull
    private final String source;
    @Nullable
    private final String reason;

    public ClassificationResult(
            @NonNull String categoryKey,
            float confidence,
            @NonNull String source,
            @Nullable String reason
    ) {
        this.categoryKey = categoryKey;
        this.confidence = confidence;
        this.source = source;
        this.reason = reason;
    }

    @NonNull
    public String getCategoryKey() {
        return categoryKey;
    }

    public float getConfidence() {
        return confidence;
    }

    @NonNull
    public String getSource() {
        return source;
    }

    @Nullable
    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "ClassificationResult{" +
                "categoryKey='" + categoryKey + '\'' +
                ", confidence=" + confidence +
                ", source='" + source + '\'' +
                ", reason='" + reason + '\'' +
                '}';
    }
}
