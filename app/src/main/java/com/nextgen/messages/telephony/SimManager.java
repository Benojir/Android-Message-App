package com.nextgen.messages.telephony;

import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.nextgen.messages.data.local.AppPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Manages Dual SIM subscription detection, default SMS subscription resolution,
 * and per-recipient SIM preference storage.
 */
public class SimManager {

    private static final String TAG = "SimManager";

    private static volatile SimManager instance;

    private final Context appContext;
    private final AppPreferences appPreferences;

    private SimManager(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.appPreferences = AppPreferences.getInstance(this.appContext);
    }

    public static SimManager getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (SimManager.class) {
                if (instance == null) {
                    instance = new SimManager(context);
                }
            }
        }
        return instance;
    }

    /**
     * Retrieves all active SIM card subscriptions currently available on the device.
     */
    @NonNull
    public List<SimInfo> getActiveSims() {
        SubscriptionManager subscriptionManager = appContext.getSystemService(SubscriptionManager.class);
        if (subscriptionManager == null) {
            Log.w(TAG, "SubscriptionManager is unavailable on this device.");
            return Collections.emptyList();
        }

        try {
            int defaultSmsSubId = SubscriptionManager.getDefaultSmsSubscriptionId();
            List<SubscriptionInfo> subList = subscriptionManager.getActiveSubscriptionInfoList();
            if (subList == null || subList.isEmpty()) {
                return Collections.emptyList();
            }

            List<SimInfo> activeSims = new ArrayList<>(subList.size());
            for (SubscriptionInfo info : subList) {
                if (info != null) {
                    int subId = info.getSubscriptionId();
                    int slotIndex = info.getSimSlotIndex();
                    String displayName = info.getDisplayName() != null ? info.getDisplayName().toString() : "SIM " + (slotIndex + 1);
                    String carrierName = info.getCarrierName() != null ? info.getCarrierName().toString() : "";
                    String countryIso = info.getCountryIso();
                    boolean isDefaultSms = (subId == defaultSmsSubId);

                    activeSims.add(new SimInfo(
                            subId,
                            slotIndex,
                            displayName,
                            carrierName,
                            countryIso,
                            isDefaultSms
                    ));
                }
            }

            Collections.sort(activeSims, Comparator.comparingInt(SimInfo::getSimSlotIndex));
            return activeSims;
        } catch (SecurityException se) {
            Log.w(TAG, "Permission denied accessing active subscription list: " + se.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            Log.e(TAG, "Error querying active subscription list", e);
            return Collections.emptyList();
        }
    }

    /**
     * Returns true if the device has two or more active SIM cards.
     */
    public boolean isDualSimActive() {
        return getActiveSims().size() > 1;
    }

    /**
     * Resolves the default SMS SIM card, or the first active SIM if no default is assigned.
     */
    @Nullable
    public SimInfo getDefaultSmsSim() {
        List<SimInfo> sims = getActiveSims();
        if (sims.isEmpty()) {
            return null;
        }

        for (SimInfo sim : sims) {
            if (sim.isDefaultSms()) {
                return sim;
            }
        }

        return sims.get(0);
    }

    /**
     * Looks up an active SIM by its subscription ID.
     */
    @Nullable
    public SimInfo getSimForSubscriptionId(int subscriptionId) {
        if (subscriptionId <= 0) {
            return null;
        }
        for (SimInfo sim : getActiveSims()) {
            if (sim.getSubscriptionId() == subscriptionId) {
                return sim;
            }
        }
        return null;
    }

    /**
     * Resolves the effective SIM to use for a recipient:
     * 1. Check if user configured a preferred SIM for this recipient that is still active.
     * 2. Fallback to default SMS SIM.
     */
    @Nullable
    public SimInfo getEffectiveSimForRecipient(@Nullable String recipient) {
        if (recipient != null && !recipient.trim().isEmpty()) {
            int preferredSubId = appPreferences.getPreferredSimForRecipient(recipient);
            if (preferredSubId > 0) {
                SimInfo preferredSim = getSimForSubscriptionId(preferredSubId);
                if (preferredSim != null) {
                    return preferredSim;
                }
            }
        }
        return getDefaultSmsSim();
    }

    /**
     * Saves the preferred SIM subscription ID for a recipient.
     */
    public void setPreferredSimForRecipient(@NonNull String recipient, int subscriptionId) {
        appPreferences.setPreferredSimForRecipient(recipient, subscriptionId);
    }
}
