package com.nextgen.messages.ui.categories;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.nextgen.messages.R;
import com.nextgen.messages.smartinbox.SmartInboxRepository;
import com.nextgen.messages.smartinbox.db.ClassificationRuleEntity;

/**
 * Utility presenting the classification rules manager dialog and rule creation form.
 */
public final class RulesManagerDialog {

    private RulesManagerDialog() {}

    public static void show(@NonNull Context context, @NonNull LifecycleOwner lifecycleOwner) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_manage_rules, null, false);
        RecyclerView rvRules = dialogView.findViewById(R.id.rv_rules);
        TextView tvNoRules = dialogView.findViewById(R.id.tv_no_rules);
        View btnAddRule = dialogView.findViewById(R.id.btn_add_rule);

        SmartInboxRepository repo = SmartInboxRepository.getInstance(context);
        ClassificationRuleAdapter ruleAdapter = new ClassificationRuleAdapter(new ClassificationRuleAdapter.OnRuleActionListener() {
            @Override
            public void onToggleRule(ClassificationRuleEntity rule, boolean isEnabled) {
                repo.toggleRule(rule, isEnabled, null);
            }

            @Override
            public void onDeleteRule(ClassificationRuleEntity rule) {
                repo.deleteRule(rule.getId(), () -> {
                    Toast.makeText(context, R.string.toast_rule_deleted, Toast.LENGTH_SHORT).show();
                });
            }
        });

        rvRules.setLayoutManager(new LinearLayoutManager(context));
        rvRules.setAdapter(ruleAdapter);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(dialogView)
                .setPositiveButton(R.string.dialog_button_close, null)
                .create();

        repo.getAllRulesLiveData().observe(lifecycleOwner, rules -> {
            if (rules == null || rules.isEmpty()) {
                tvNoRules.setVisibility(View.VISIBLE);
                rvRules.setVisibility(View.GONE);
                ruleAdapter.setRules(null);
            } else {
                tvNoRules.setVisibility(View.GONE);
                rvRules.setVisibility(View.VISIBLE);
                ruleAdapter.setRules(rules);
            }
        });

        btnAddRule.setOnClickListener(v -> showAddRuleDialog(context, repo));

        dialog.show();
    }

    private static void showAddRuleDialog(@NonNull Context context, @NonNull SmartInboxRepository repo) {
        View addView = LayoutInflater.from(context).inflate(R.layout.dialog_add_rule, null, false);
        EditText etRuleName = addView.findViewById(R.id.et_rule_name);
        RadioGroup rgRuleType = addView.findViewById(R.id.rg_rule_type);
        EditText etRuleValue = addView.findViewById(R.id.et_rule_value);
        ChipGroup chipGroupCategories = addView.findViewById(R.id.chip_group_rule_categories);

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.dialog_new_rule_title)
                .setView(addView)
                .setPositiveButton(R.string.action_add_rule, (dialog, which) -> {
                    String ruleName = etRuleName.getText() != null ? etRuleName.getText().toString().trim() : "";
                    String pattern = etRuleValue.getText() != null ? etRuleValue.getText().toString().trim() : "";

                    if (TextUtils.isEmpty(ruleName) || TextUtils.isEmpty(pattern)) {
                        Toast.makeText(context, "Please fill in all rule fields", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String ruleType = ClassificationRuleEntity.TYPE_BODY_CONTAINS;
                    int checkedRadio = rgRuleType.getCheckedRadioButtonId();
                    if (checkedRadio == R.id.rb_sender_equals) {
                        ruleType = ClassificationRuleEntity.TYPE_SENDER_EQUALS;
                    } else if (checkedRadio == R.id.rb_sender_contains) {
                        ruleType = ClassificationRuleEntity.TYPE_SENDER_CONTAINS;
                    } else if (checkedRadio == R.id.rb_body_starts_with) {
                        ruleType = ClassificationRuleEntity.TYPE_BODY_STARTS_WITH;
                    }

                    int checkedChipId = chipGroupCategories.getCheckedChipId();
                    String targetCategory = "personal";
                    if (checkedChipId != View.NO_ID) {
                        Chip chip = chipGroupCategories.findViewById(checkedChipId);
                        if (chip != null && chip.getTag() != null) {
                            targetCategory = chip.getTag().toString();
                        }
                    }

                    repo.addRule(ruleName, ruleType, pattern, targetCategory, 1, () -> {
                        Toast.makeText(context, R.string.toast_rule_created, Toast.LENGTH_SHORT).show();
                    });
                })
                .setNegativeButton(R.string.dialog_button_cancel, null)
                .show();
    }
}
