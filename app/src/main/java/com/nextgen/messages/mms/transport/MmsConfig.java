package com.nextgen.messages.mms.transport;

import android.content.Context;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import androidx.annotation.NonNull;

/**
 * Accesses carrier MMS configuration and constraints per active subscription.
 */
public final class MmsConfig {

    public static final int DEFAULT_MAX_MESSAGE_SIZE = 600 * 1024; // 600 KB standard
    public static final int DEFAULT_MAX_IMAGE_WIDTH = 1920;
    public static final int DEFAULT_MAX_IMAGE_HEIGHT = 1080;

    private MmsConfig() {}

    /**
     * Retrieves the maximum MMS size allowed by the carrier for the given subscription in bytes.
     */
    public static int getMaxMessageSize(@NonNull Context context, int subscriptionId) {
        try {
            CarrierConfigManager manager = (CarrierConfigManager) context.getSystemService(Context.CARRIER_CONFIG_SERVICE);
            if (manager != null) {
                int targetSubId = subscriptionId > 0 ? subscriptionId : SubscriptionManager.getDefaultSmsSubscriptionId();
                PersistableBundle bundle = manager.getConfigForSubId(targetSubId);
                if (bundle != null) {
                    int size = bundle.getInt(CarrierConfigManager.KEY_MMS_MAX_MESSAGE_SIZE_INT, DEFAULT_MAX_MESSAGE_SIZE);
                    if (size > 0) {
                        return size;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return DEFAULT_MAX_MESSAGE_SIZE;
    }

    /**
     * Checks if carrier supports Group MMS for this subscription.
     */
    public static boolean isCarrierGroupMmsSupported(@NonNull Context context, int subscriptionId) {
        try {
            CarrierConfigManager manager = (CarrierConfigManager) context.getSystemService(Context.CARRIER_CONFIG_SERVICE);
            if (manager != null) {
                int targetSubId = subscriptionId > 0 ? subscriptionId : SubscriptionManager.getDefaultSmsSubscriptionId();
                PersistableBundle bundle = manager.getConfigForSubId(targetSubId);
                if (bundle != null) {
                    return bundle.getBoolean(CarrierConfigManager.KEY_MMS_GROUP_MMS_ENABLED_BOOL, true);
                }
            }
        } catch (Exception ignored) {
        }
        return true;
    }
}
