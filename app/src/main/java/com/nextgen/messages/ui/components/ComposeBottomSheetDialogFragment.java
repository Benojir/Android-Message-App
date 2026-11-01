package com.nextgen.messages.ui.components;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.nextgen.messages.databinding.DialogComposePlaceholderBinding;
import com.nextgen.messages.ui.compose.ComposeActivity;

/**
 * Modern Bottom Sheet modal informing the user about the Step 2 compose features
 * and providing direct navigation into {@link ComposeActivity}.
 */
public class ComposeBottomSheetDialogFragment extends BottomSheetDialogFragment {

    public static final String TAG = "ComposeBottomSheetDialogFragment";

    private DialogComposePlaceholderBinding binding;

    public static ComposeBottomSheetDialogFragment newInstance() {
        return new ComposeBottomSheetDialogFragment();
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = DialogComposePlaceholderBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnDismiss.setOnClickListener(v -> {
            dismiss();
            startActivity(new Intent(requireContext(), ComposeActivity.class));
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
