package com.nextgen.messages.ui.categories;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.snackbar.Snackbar;
import com.nextgen.messages.R;
import com.nextgen.messages.data.model.CategoryItem;
import com.nextgen.messages.databinding.FragmentCategoriesBinding;

/**
 * Fragment presenting the smart message categories roadmap.
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
        setupRecyclerView();
        observeViewModel();
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(0, insets.top, 0, 0);
            return windowInsets;
        });
    }

    private void setupRecyclerView() {
        adapter = new CategoryAdapter(this::onCategoryClicked);
        binding.rvCategories.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvCategories.setAdapter(adapter);
    }

    private void onCategoryClicked(CategoryItem item) {
        String categoryName = getString(item.getTitleRes());
        String message = getString(R.string.category_click_toast, categoryName);
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT).show();
    }

    private void observeViewModel() {
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
