package com.nextgen.messages.ui.settings;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.nextgen.messages.data.local.AppPreferences;
import com.nextgen.messages.telephony.SmsRoleManager;
import com.nextgen.messages.utils.ThemeHelper;

/**
 * ViewModel managing settings preferences, theme mode, dynamic color state,
 * and default SMS role status monitoring.
 */
public class SettingsViewModel extends AndroidViewModel {

    private final AppPreferences preferences;
    private final SmsRoleManager smsRoleManager;
    private final MutableLiveData<Integer> themeMode = new MutableLiveData<>();
    private final MutableLiveData<Boolean> dynamicColorsEnabled = new MutableLiveData<>();
    private final MutableLiveData<SmsRoleManager.RoleStatus> roleStatus = new MutableLiveData<>();

    public SettingsViewModel(@NonNull Application application) {
        super(application);
        this.preferences = AppPreferences.getInstance(application);
        this.smsRoleManager = new SmsRoleManager(application);
        loadPreferences();
        refreshRoleStatus();
    }

    private void loadPreferences() {
        themeMode.setValue(preferences.getThemeMode());
        dynamicColorsEnabled.setValue(preferences.isDynamicColorsEnabled());
    }

    public void refreshRoleStatus() {
        roleStatus.setValue(smsRoleManager.getRoleStatus());
    }

    public LiveData<SmsRoleManager.RoleStatus> getRoleStatus() {
        return roleStatus;
    }

    public SmsRoleManager getSmsRoleManager() {
        return smsRoleManager;
    }

    public LiveData<Integer> getThemeMode() {
        return themeMode;
    }

    public LiveData<Boolean> getDynamicColorsEnabled() {
        return dynamicColorsEnabled;
    }

    public void setThemeMode(int mode) {
        preferences.setThemeMode(mode);
        themeMode.setValue(mode);
        ThemeHelper.applyTheme(mode);
    }

    public void setDynamicColorsEnabled(boolean enabled) {
        preferences.setDynamicColorsEnabled(enabled);
        dynamicColorsEnabled.setValue(enabled);
    }
}
