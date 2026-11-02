package com.nextgen.messages.data.repository;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import com.nextgen.messages.smartinbox.db.BlockedSenderEntity;
import com.nextgen.messages.smartinbox.db.SmartInboxDao;
import com.nextgen.messages.smartinbox.db.SmartInboxDatabase;
import com.nextgen.messages.utils.AppExecutors;
import java.util.List;
import java.util.Locale;

/**
 * Repository managing blocked phone numbers and alphanumeric sender IDs with
 * robust normalization (handling international prefixes, spaces, and formatting).
 */
public class BlockedSenderRepository {

    private static volatile BlockedSenderRepository instance;
    private final SmartInboxDao dao;

    private BlockedSenderRepository(@NonNull Context context) {
        this.dao = SmartInboxDatabase.getInstance(context).smartInboxDao();
    }

    public static BlockedSenderRepository getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (BlockedSenderRepository.class) {
                if (instance == null) {
                    instance = new BlockedSenderRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /**
     * Normalizes a phone number or alphanumeric sender ID for stable matching.
     */
    @NonNull
    public static String normalizeIdentifier(@Nullable String address) {
        if (address == null || address.trim().isEmpty()) {
            return "";
        }
        String trimmed = address.trim();
        // Check if alphanumeric sender ID (contains letters)
        boolean hasLetters = false;
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isLetter(trimmed.charAt(i))) {
                hasLetters = true;
                break;
            }
        }

        if (hasLetters) {
            return trimmed.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        } else {
            // Numeric phone number: strip formatting
            return trimmed.replaceAll("[^0-9]", "");
        }
    }

    /**
     * Checks if a phone number or sender ID is blocked.
     */
    public boolean isBlocked(@Nullable String address) {
        if (address == null || address.trim().isEmpty()) {
            return false;
        }
        String normalized = normalizeIdentifier(address);
        if (normalized.isEmpty()) {
            return false;
        }

        if (dao.isSenderBlocked(normalized)) {
            return true;
        }

        // For phone numbers >= 10 digits, match on last 10 digits to handle country code variants
        if (normalized.length() >= 10) {
            String last10 = normalized.substring(normalized.length() - 10);
            List<String> blocked = dao.getAllBlockedNormalizedIdentifiers();
            for (String item : blocked) {
                if (item.length() >= 10 && item.endsWith(last10)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Blocks a sender address with an optional reason.
     */
    public void blockSender(@NonNull String address, @Nullable String reason, @Nullable Runnable onComplete) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            String normalized = normalizeIdentifier(address);
            if (!normalized.isEmpty()) {
                BlockedSenderEntity entity = BlockedSenderEntity.create(
                        address,
                        normalized,
                        System.currentTimeMillis(),
                        reason
                );
                dao.insertBlockedSender(entity);
            }
            if (onComplete != null) {
                AppExecutors.getInstance().mainThread().execute(onComplete);
            }
        });
    }

    /**
     * Unblocks a sender address.
     */
    public void unblockSender(@NonNull String address, @Nullable Runnable onComplete) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            String normalized = normalizeIdentifier(address);
            if (!normalized.isEmpty()) {
                dao.deleteBlockedSenderByNormalized(normalized);
                if (normalized.length() >= 10) {
                    String last10 = normalized.substring(normalized.length() - 10);
                    List<BlockedSenderEntity> all = dao.getAllBlockedSendersSync();
                    for (BlockedSenderEntity item : all) {
                        if (item.getNormalizedIdentifier().length() >= 10
                                && item.getNormalizedIdentifier().endsWith(last10)) {
                            dao.deleteBlockedSenderById(item.getId());
                        }
                    }
                }
            }
            if (onComplete != null) {
                AppExecutors.getInstance().mainThread().execute(onComplete);
            }
        });
    }

    public LiveData<List<BlockedSenderEntity>> getAllBlockedSendersLiveData() {
        return dao.getAllBlockedSendersLiveData();
    }

    public List<BlockedSenderEntity> getAllBlockedSendersSync() {
        return dao.getAllBlockedSendersSync();
    }
}
