package com.nextgen.messages.telephony;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Objects;

/**
 * Immutable model representing an active SIM card subscription.
 */
public class SimInfo {

    private final int subscriptionId;
    private final int simSlotIndex;
    @NonNull
    private final String displayName;
    @NonNull
    private final String carrierName;
    @Nullable
    private final String countryIso;
    private final boolean isDefaultSms;

    public SimInfo(
            int subscriptionId,
            int simSlotIndex,
            @NonNull String displayName,
            @NonNull String carrierName,
            @Nullable String countryIso,
            boolean isDefaultSms
    ) {
        this.subscriptionId = subscriptionId;
        this.simSlotIndex = simSlotIndex;
        this.displayName = displayName;
        this.carrierName = carrierName;
        this.countryIso = countryIso;
        this.isDefaultSms = isDefaultSms;
    }

    public int getSubscriptionId() {
        return subscriptionId;
    }

    public int getSimSlotIndex() {
        return simSlotIndex;
    }

    public int getSlotNumber() {
        return simSlotIndex + 1;
    }

    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    @NonNull
    public String getCarrierName() {
        return carrierName;
    }

    @Nullable
    public String getCountryIso() {
        return countryIso;
    }

    public boolean isDefaultSms() {
        return isDefaultSms;
    }

    @NonNull
    public String getLabel() {
        return "SIM " + getSlotNumber();
    }

    @NonNull
    public String getFullLabel() {
        String name = displayName.trim();
        if (name.isEmpty() || name.equalsIgnoreCase(getLabel())) {
            return getLabel() + (!carrierName.isEmpty() ? " (" + carrierName + ")" : "");
        }
        return getLabel() + " (" + name + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SimInfo simInfo = (SimInfo) o;
        return subscriptionId == simInfo.subscriptionId
                && simSlotIndex == simInfo.simSlotIndex
                && isDefaultSms == simInfo.isDefaultSms
                && Objects.equals(displayName, simInfo.displayName)
                && Objects.equals(carrierName, simInfo.carrierName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subscriptionId, simSlotIndex, displayName, carrierName, isDefaultSms);
    }

    @NonNull
    @Override
    public String toString() {
        return "SimInfo{" +
                "subId=" + subscriptionId +
                ", slot=" + simSlotIndex +
                ", label='" + getFullLabel() + '\'' +
                ", isDefaultSms=" + isDefaultSms +
                '}';
    }
}
