package com.nextgen.messages.ui.main;

import android.os.Bundle;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.nextgen.messages.R;
import com.nextgen.messages.databinding.ActivityMainBinding;
import com.nextgen.messages.ui.categories.CategoriesFragment;
import com.nextgen.messages.ui.inbox.InboxFragment;
import com.nextgen.messages.ui.settings.SettingsFragment;

/**
 * Modern, edge-to-edge MainActivity hosting bottom navigation and primary application fragments.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG_MESSAGES = "fragment_messages";
    private static final String TAG_CATEGORIES = "fragment_categories";
    private static final String TAG_SETTINGS = "fragment_settings";
    private static final String KEY_SELECTED_TAB = "key_selected_tab";

    private ActivityMainBinding binding;
    private int currentSelectedTabId = R.id.nav_messages;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Enable edge-to-edge system window decor fitting before setContentView
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupWindowInsets();
        setupBottomNavigation();
        setupBackPressHandling();

        if (savedInstanceState != null) {
            currentSelectedTabId = savedInstanceState.getInt(KEY_SELECTED_TAB, R.id.nav_messages);
        } else {
            showTabFragment(R.id.nav_messages);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_SELECTED_TAB, currentSelectedTabId);
    }

    private void setupWindowInsets() {
        // Pad bottom navigation bar for navigation bar and gesture pill insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            view.setPadding(0, 0, 0, insets.bottom);
            return windowInsets;
        });
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == currentSelectedTabId) {
                return true;
            }
            showTabFragment(itemId);
            currentSelectedTabId = itemId;
            return true;
        });
    }

    private void showTabFragment(int tabId) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        ft.setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out
        );

        Fragment messagesFragment = fm.findFragmentByTag(TAG_MESSAGES);
        Fragment categoriesFragment = fm.findFragmentByTag(TAG_CATEGORIES);
        Fragment settingsFragment = fm.findFragmentByTag(TAG_SETTINGS);

        // Hide all active fragments first
        if (messagesFragment != null) ft.hide(messagesFragment);
        if (categoriesFragment != null) ft.hide(categoriesFragment);
        if (settingsFragment != null) ft.hide(settingsFragment);

        if (tabId == R.id.nav_messages) {
            if (messagesFragment == null) {
                messagesFragment = InboxFragment.newInstance();
                ft.add(R.id.fragment_container, messagesFragment, TAG_MESSAGES);
            } else {
                ft.show(messagesFragment);
            }
        } else if (tabId == R.id.nav_categories) {
            if (categoriesFragment == null) {
                categoriesFragment = CategoriesFragment.newInstance();
                ft.add(R.id.fragment_container, categoriesFragment, TAG_CATEGORIES);
            } else {
                ft.show(categoriesFragment);
            }
        } else if (tabId == R.id.nav_settings) {
            if (settingsFragment == null) {
                settingsFragment = SettingsFragment.newInstance();
                ft.add(R.id.fragment_container, settingsFragment, TAG_SETTINGS);
            } else {
                ft.show(settingsFragment);
            }
        }

        ft.commitAllowingStateLoss();
    }

    private void setupBackPressHandling() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentSelectedTabId != R.id.nav_messages) {
                    binding.bottomNavigation.setSelectedItemId(R.id.nav_messages);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }
}
