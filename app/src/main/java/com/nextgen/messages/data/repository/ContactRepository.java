package com.nextgen.messages.data.repository;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import android.util.LruCache;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.nextgen.messages.data.model.ContactInfo;

/**
 * Repository responsible for resolving phone numbers to contact names and photos.
 * Employs an in-memory LruCache to minimize repeated ContentResolver queries.
 */
public class ContactRepository {

    private static final String TAG = "ContactRepository";
    private static final int CACHE_SIZE = 500;

    private static volatile ContactRepository instance;

    private final Context appContext;
    private final LruCache<String, ContactInfo> contactCache;

    private ContactRepository(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.contactCache = new LruCache<>(CACHE_SIZE);
    }

    public static ContactRepository getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (ContactRepository.class) {
                if (instance == null) {
                    instance = new ContactRepository(context);
                }
            }
        }
        return instance;
    }

    /**
     * Resolves contact details for the given address/number.
     * Checks memory cache first. If not cached, queries ContactsContract.PhoneLookup if permission is granted.
     * Returns a fallback ContactInfo if not found or permission is missing.
     */
    @NonNull
    public ContactInfo resolveContact(@Nullable String rawAddress) {
        if (rawAddress == null || rawAddress.trim().isEmpty()) {
            return ContactInfo.fallback(rawAddress);
        }

        String cacheKey = rawAddress.trim();
        synchronized (contactCache) {
            ContactInfo cached = contactCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        ContactInfo resolved = queryContact(rawAddress);
        synchronized (contactCache) {
            contactCache.put(cacheKey, resolved);
        }
        return resolved;
    }

    /**
     * Directly queries ContactsContract.PhoneLookup.
     */
    @NonNull
    private ContactInfo queryContact(@NonNull String rawAddress) {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            return ContactInfo.fallback(rawAddress);
        }

        Uri uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(rawAddress)
        );

        String[] projection = new String[]{
                ContactsContract.PhoneLookup._ID,
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.PHOTO_URI,
                ContactsContract.PhoneLookup.LOOKUP_KEY,
                ContactsContract.PhoneLookup.NUMBER
        };

        ContentResolver resolver = appContext.getContentResolver();
        try (Cursor cursor = resolver.query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
                int photoIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI);
                int lookupIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.LOOKUP_KEY);
                int numberIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.NUMBER);

                String displayName = nameIdx >= 0 ? cursor.getString(nameIdx) : null;
                String photoUriStr = photoIdx >= 0 ? cursor.getString(photoIdx) : null;
                String lookupKey = lookupIdx >= 0 ? cursor.getString(lookupIdx) : null;
                String normalizedNumber = numberIdx >= 0 ? cursor.getString(numberIdx) : rawAddress;

                Uri photoUri = photoUriStr != null ? Uri.parse(photoUriStr) : null;

                return new ContactInfo(displayName, photoUri, lookupKey, normalizedNumber);
            }
        } catch (SecurityException se) {
            Log.w(TAG, "READ_CONTACTS permission denied or restricted: " + se.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error querying ContactsContract: " + e.getMessage(), e);
        }

        return ContactInfo.fallback(rawAddress);
    }

    /**
     * Clears the in-memory contact cache. Call when contact database changes or on refresh.
     */
    public void clearCache() {
        synchronized (contactCache) {
            contactCache.evictAll();
        }
    }
}
