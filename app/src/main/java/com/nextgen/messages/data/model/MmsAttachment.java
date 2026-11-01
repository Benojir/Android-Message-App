package com.nextgen.messages.data.model;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.Serializable;
import java.util.Objects;

/**
 * Data model representing a multimedia attachment in an MMS message.
 */
public class MmsAttachment implements Serializable {

    private final long partId;
    private final long messageId;
    @NonNull
    private final Uri uri;
    @NonNull
    private final String contentType;
    @Nullable
    private final String fileName;
    @Nullable
    private final String name;
    private final long size;

    public MmsAttachment(
            long partId,
            long messageId,
            @NonNull Uri uri,
            @NonNull String contentType,
            @Nullable String fileName,
            @Nullable String name,
            long size
    ) {
        this.partId = partId;
        this.messageId = messageId;
        this.uri = uri;
        this.contentType = contentType.toLowerCase();
        this.fileName = fileName;
        this.name = name;
        this.size = size;
    }

    public MmsAttachment(
            long partId,
            long messageId,
            @Nullable Uri uri,
            @NonNull String contentType,
            @Nullable String fileName,
            long size
    ) {
        this(partId, messageId, uri != null ? uri : Uri.EMPTY, contentType, fileName, fileName, size);
    }

    public long getPartId() {
        return partId;
    }

    public long getMessageId() {
        return messageId;
    }

    @NonNull
    public Uri getUri() {
        return uri;
    }

    @NonNull
    public Uri getPartUri() {
        return uri;
    }

    @NonNull
    public String getContentType() {
        return contentType;
    }

    @Nullable
    public String getFileName() {
        return fileName;
    }

    @Nullable
    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }

    @NonNull
    public String getFormattedSize() {
        if (size <= 0) return "";
        if (size < 1024) return size + " B";
        long kb = size / 1024;
        if (kb < 1024) return kb + " KB";
        return String.format(java.util.Locale.US, "%.1f MB", size / (1024.0 * 1024.0));
    }

    public boolean isImage() {
        return contentType.startsWith("image/");
    }

    public boolean isAudio() {
        return contentType.startsWith("audio/");
    }

    public boolean isVideo() {
        return contentType.startsWith("video/");
    }

    public boolean isText() {
        return contentType.startsWith("text/");
    }

    public boolean isSmil() {
        return contentType.equalsIgnoreCase("application/smil");
    }

    public boolean isMedia() {
        return isImage() || isAudio() || isVideo();
    }

    @NonNull
    public String getDisplayFileName() {
        if (fileName != null && !fileName.trim().isEmpty()) {
            return fileName.trim();
        }
        if (name != null && !name.trim().isEmpty()) {
            return name.trim();
        }
        if (isImage()) return "image.jpg";
        if (isVideo()) return "video.mp4";
        if (isAudio()) return "audio.amr";
        return "attachment";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MmsAttachment that = (MmsAttachment) o;
        return partId == that.partId
                && messageId == that.messageId
                && size == that.size
                && uri.equals(that.uri)
                && contentType.equals(that.contentType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(partId, messageId, uri, contentType, size);
    }

    @NonNull
    @Override
    public String toString() {
        return "MmsAttachment{" +
                "partId=" + partId +
                ", uri=" + uri +
                ", contentType='" + contentType + '\'' +
                ", size=" + size +
                '}';
    }
}
