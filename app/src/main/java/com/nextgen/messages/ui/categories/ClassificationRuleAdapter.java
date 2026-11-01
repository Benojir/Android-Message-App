package com.nextgen.messages.ui.categories;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.nextgen.messages.databinding.ItemRuleCardBinding;
import com.nextgen.messages.smartinbox.db.ClassificationRuleEntity;
import com.nextgen.messages.ui.conversation.MessageDetailsDialog;
import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for displaying and managing user-defined classification rules.
 */
public class ClassificationRuleAdapter extends RecyclerView.Adapter<ClassificationRuleAdapter.RuleViewHolder> {

    public interface OnRuleActionListener {
        void onToggleRule(ClassificationRuleEntity rule, boolean isEnabled);
        void onDeleteRule(ClassificationRuleEntity rule);
    }

    private final List<ClassificationRuleEntity> rules = new ArrayList<>();
    private final OnRuleActionListener listener;

    public ClassificationRuleAdapter(OnRuleActionListener listener) {
        this.listener = listener;
    }

    public void setRules(List<ClassificationRuleEntity> newRules) {
        rules.clear();
        if (newRules != null) {
            rules.addAll(newRules);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RuleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemRuleCardBinding binding = ItemRuleCardBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new RuleViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RuleViewHolder holder, int position) {
        holder.bind(rules.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return rules.size();
    }

    static class RuleViewHolder extends RecyclerView.ViewHolder {

        private final ItemRuleCardBinding binding;

        RuleViewHolder(ItemRuleCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ClassificationRuleEntity rule, OnRuleActionListener listener) {
            Context context = itemView.getContext();

            binding.tvRuleName.setText(rule.getRuleName());

            String typeDesc;
            switch (rule.getRuleType()) {
                case ClassificationRuleEntity.TYPE_SENDER_EQUALS:
                    typeDesc = "Sender equals \"" + rule.getMatchValue() + "\"";
                    break;
                case ClassificationRuleEntity.TYPE_SENDER_CONTAINS:
                    typeDesc = "Sender contains \"" + rule.getMatchValue() + "\"";
                    break;
                case ClassificationRuleEntity.TYPE_BODY_STARTS_WITH:
                    typeDesc = "Body starts with \"" + rule.getMatchValue() + "\"";
                    break;
                case ClassificationRuleEntity.TYPE_BODY_CONTAINS:
                default:
                    typeDesc = "Body contains \"" + rule.getMatchValue() + "\"";
                    break;
            }

            String targetCategoryName = MessageDetailsDialog.getLocalizedCategoryName(context, rule.getTargetCategoryKey());
            binding.tvRuleDetails.setText(typeDesc + " → " + targetCategoryName);

            // Avoid triggering listener during bind
            binding.switchRuleEnabled.setOnCheckedChangeListener(null);
            binding.switchRuleEnabled.setChecked(rule.isEnabled());
            binding.switchRuleEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (listener != null) {
                    listener.onToggleRule(rule, isChecked);
                }
            });

            binding.btnDeleteRule.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteRule(rule);
                }
            });
        }
    }
}
