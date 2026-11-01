package com.nextgen.messages.data.model;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Objects;

/**
 * Model representing resolved contact details from ContactsContract.
 */
public class ContactInfo {

    @Nullable
    private final String displayName;
    @Nullable
    private final Uri photoUri;
    @Nullable
    private final String lookupKey;
    @Nullable
    private final String normalizedPhoneNumber;

    public ContactInfo(
            @Nullable String displayName,
            @Nullable Uri photoUri,
            @Nullable String lookupKey,
            @Nullable String normalizedPhoneNumber
    ) {
        this.displayName = displayName;
        this.photoUri = photoUri;
        this.lookupKey = lookupKey;
        this.normalizedPhoneNumber = normalizedPhoneNumber;
    }

    public static ContactInfo fallback(@Nullable String address) {
        return new ContactInfo(null, null, null, address);
    }

    @Nullable
    public String getDisplayName() {
        return displayName;
    }

    @Nullable
    public Uri getPhotoUri() {
        return photoUri;
    }

    @Nullable
    public String getLookupKey() {
        return lookupKey;
    }

    @Nullable
    public String getNormalizedPhoneNumber() {
        return normalizedPhoneNumber;
    }

    public boolean hasName() {
        return displayName != null && !displayName.trim().isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContactInfo that = (ContactInfo) o;
        return Objects.equals(displayName, that.displayName)
                && Objects.equals(photoUri, that.photoUri)
                && Objects.equals(lookupKey, that.lookupKey)
                && Objects.equals(normalizedPhoneNumber, that.normalizedPhoneNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(displayName, photoUri, lookupKey, normalizedPhoneNumber);
    }

    @NonNull
    @Override
    public String toString() {
        return "ContactInfo{" +
                "displayName='" + displayName + '\'' +
                ", photoUri=" + photoUri +
                '}';
    }
}
