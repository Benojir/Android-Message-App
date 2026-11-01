package com.nextgen.messages.ui.categories;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.nextgen.messages.R;
import com.nextgen.messages.data.model.CategoryItem;
import com.nextgen.messages.databinding.FragmentCategoriesBinding;
import com.nextgen.messages.smartinbox.SmartInboxRepository;
import com.nextgen.messages.smartinbox.db.ClassificationRuleEntity;
import com.nextgen.messages.ui.conversation.MessageDetailsDialog;
import com.nextgen.messages.ui.main.MainActivity;
import java.util.List;

/**
 * Fragment presenting Smart Inbox categories, dynamic message and unread counts,
 * custom category creation and deletion, and classification rules management.
 */
public class CategoriesFragment extends Fragment {

    private FragmentCategoriesBinding binding;
    private CategoriesViewModel viewModel;
    private CategoryAdapter adapter;

    public static CategoriesFragment newInstance() {
        return new CategoriesFragment();
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentCategoriesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(CategoriesViewModel.class);

        setupWindowInsets();
        setupToolbar();
        setupRecyclerView();
        observeViewModel();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) {
            viewModel.loadCategories();
        }
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(0, insets.top, 0, 0);
            return windowInsets;
        });
    }

    private void setupToolbar() {
        binding.toolbar.inflateMenu(R.menu.categories_menu);
        binding.toolbar.setOnMenuItemClickListener(this::onToolbarMenuItemClicked);
    }

    private boolean onToolbarMenuItemClicked(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_new_category) {
            showCreateCategoryDialog();
            return true;
        } else if (id == R.id.action_manage_rules) {
            RulesManagerDialog.show(requireContext(), getViewLifecycleOwner());
            return true;
        }
        return false;
    }

    private void setupRecyclerView() {
        adapter = new CategoryAdapter(this::onCategoryClicked);
        adapter.setOnCategoryOptionListener(this::onCategoryDelete);

        binding.rvCategories.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvCategories.setAdapter(adapter);
    }

    private void onCategoryClicked(@NonNull CategoryItem item) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).selectCategoryInInbox(item.getId());
        } else {
            String title = item.getCustomTitle() != null ? item.getCustomTitle() : getString(item.getTitleRes());
            Snackbar.make(binding.getRoot(), title, Snackbar.LENGTH_SHORT).show();
        }
    }

    private void onCategoryDelete(@NonNull CategoryItem item) {
        String categoryName = item.getCustomTitle() != null ? item.getCustomTitle() : item.getId();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_delete_category_title)
                .setMessage(getString(R.string.dialog_delete_category_message, categoryName))
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    viewModel.deleteCustomCategory(item.getId());
                    Snackbar.make(binding.getRoot(), "Category deleted", Snackbar.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.dialog_button_cancel, null)
                .show();
    }

    private void showCreateCategoryDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_category, null, false);
        EditText etName = dialogView.findViewById(R.id.et_category_name);
        ChipGroup chipGroupColors = dialogView.findViewById(R.id.chip_group_colors);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_create_category_title)
                .setView(dialogView)
                .setPositiveButton(R.string.dialog_button_ok, (dialog, which) -> {
                    String name = etName.getText() != null ? etName.getText().toString().trim() : "";
                    if (TextUtils.isEmpty(name)) {
                        Toast.makeText(requireContext(), "Category name cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int checkedId = chipGroupColors.getCheckedChipId();
                    String colorHex = "#009688"; // Teal default
                    if (checkedId == R.id.chip_color_indigo) {
                        colorHex = "#3F51B5";
                    } else if (checkedId == R.id.chip_color_pink) {
                        colorHex = "#E91E63";
                    } else if (checkedId == R.id.chip_color_amber) {
                        colorHex = "#FF8F00";
                    } else if (checkedId == R.id.chip_color_brown) {
                        colorHex = "#795548";
                    } else if (checkedId == R.id.chip_color_cyan) {
                        colorHex = "#00838F";
                    }

                    viewModel.createCustomCategory(name, colorHex);
                    Snackbar.make(binding.getRoot(), "Custom category created", Snackbar.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.dialog_button_cancel, null)
                .show();
    }

    private void observeViewModel() {
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> {
            if (binding != null) {
                binding.progressLoading.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE);
            }
        });

        viewModel.getCategories().observe(getViewLifecycleOwner(), categories -> {
            if (categories != null) {
                adapter.setItems(categories);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
