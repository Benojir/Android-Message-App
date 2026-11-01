package com.nextgen.messages.ui.settings;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.nextgen.messages.data.local.AppPreferences;
import com.nextgen.messages.utils.ThemeHelper;

/**
 * ViewModel managing settings preferences, theme mode, and dynamic color state.
 */
public class SettingsViewModel extends AndroidViewModel {

    private final AppPreferences preferences;
    private final MutableLiveData<Integer> themeMode = new MutableLiveData<>();
    private final MutableLiveData<Boolean> dynamicColorsEnabled = new MutableLiveData<>();

    public SettingsViewModel(@NonNull Application application) {
        super(application);
        this.preferences = AppPreferences.getInstance(application);
        loadPreferences();
    }

    private void loadPreferences() {
        themeMode.setValue(preferences.getThemeMode());
        dynamicColorsEnabled.setValue(preferences.isDynamicColorsEnabled());
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
