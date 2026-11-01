package com.nextgen.messages.ui.categories;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.nextgen.messages.R;
import com.nextgen.messages.data.model.CategoryItem;
import com.nextgen.messages.databinding.ItemCategoryCardBinding;
import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for displaying smart message categories with live counts,
 * unread badges, and custom category management actions.
 */
public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    public interface OnCategoryClickListener {
        void onCategoryClicked(CategoryItem item);
    }

    public interface OnCategoryOptionListener {
        void onCategoryDelete(CategoryItem item);
    }

    private final List<CategoryItem> items = new ArrayList<>();
    private final OnCategoryClickListener listener;
    private OnCategoryOptionListener optionListener;

    public CategoryAdapter(OnCategoryClickListener listener) {
        this.listener = listener;
    }

    public void setOnCategoryOptionListener(OnCategoryOptionListener optionListener) {
        this.optionListener = optionListener;
    }

    public void setItems(List<CategoryItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCategoryCardBinding binding = ItemCategoryCardBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new CategoryViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        holder.bind(items.get(position), listener, optionListener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class CategoryViewHolder extends RecyclerView.ViewHolder {

        private final ItemCategoryCardBinding binding;

        CategoryViewHolder(ItemCategoryCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(
                CategoryItem item,
                OnCategoryClickListener clickListener,
                OnCategoryOptionListener optionListener
        ) {
            Context context = itemView.getContext();

            // Title
            if (item.getCustomTitle() != null) {
                binding.tvCategoryTitle.setText(item.getCustomTitle());
            } else if (item.getTitleRes() != 0) {
                binding.tvCategoryTitle.setText(item.getTitleRes());
            } else {
                binding.tvCategoryTitle.setText(item.getId());
            }

            // Description
            if (item.getCustomDesc() != null) {
                binding.tvCategoryDesc.setText(item.getCustomDesc());
            } else if (item.getDescRes() != 0) {
                binding.tvCategoryDesc.setText(item.getDescRes());
            } else {
                binding.tvCategoryDesc.setText("");
            }

            // Badge / Count
            if (item.getUnreadCount() > 0) {
                binding.tvCategoryBadge.setText(context.getString(R.string.category_unread_count_format, item.getUnreadCount()));
                binding.tvCategoryBadge.setVisibility(View.VISIBLE);
            } else if (item.getMessageCount() > 0) {
                binding.tvCategoryBadge.setText(context.getString(R.string.category_count_format, item.getMessageCount()));
                binding.tvCategoryBadge.setVisibility(View.VISIBLE);
            } else if (item.getCustomBadgeText() != null) {
                binding.tvCategoryBadge.setText(item.getCustomBadgeText());
                binding.tvCategoryBadge.setVisibility(View.VISIBLE);
            } else if (item.getBadgeRes() != 0) {
                binding.tvCategoryBadge.setText(item.getBadgeRes());
                binding.tvCategoryBadge.setVisibility(View.VISIBLE);
            } else {
                binding.tvCategoryBadge.setVisibility(View.GONE);
            }

            // Icon
            if (item.getIconRes() != 0) {
                binding.ivCategoryIcon.setImageResource(item.getIconRes());
            } else {
                binding.ivCategoryIcon.setImageResource(R.drawable.ic_category_other);
            }

            // Color tints
            int iconColor;
            int containerColor;

            if (item.getColorInt() != 0) {
                iconColor = item.getColorInt();
            } else if (item.getColorRes() != 0) {
                iconColor = ContextCompat.getColor(context, item.getColorRes());
            } else {
                iconColor = ContextCompat.getColor(context, R.color.md_theme_light_primary);
            }

            if (item.getContainerColorInt() != 0) {
                containerColor = item.getContainerColorInt();
            } else if (item.getContainerColorRes() != 0) {
                containerColor = ContextCompat.getColor(context, item.getContainerColorRes());
            } else {
                containerColor = ContextCompat.getColor(context, R.color.md_theme_light_primaryContainer);
            }

            binding.ivCategoryIcon.setImageTintList(ColorStateList.valueOf(iconColor));
            binding.layoutIconContainer.setBackgroundTintList(ColorStateList.valueOf(containerColor));

            // Options menu for custom categories
            if (!item.isBuiltIn()) {
                binding.btnCategoryOptions.setVisibility(View.VISIBLE);
                binding.btnCategoryOptions.setOnClickListener(v -> {
                    PopupMenu popup = new PopupMenu(context, v);
                    popup.getMenu().add(0, 1, 0, context.getString(R.string.action_delete));
                    popup.setOnMenuItemClickListener(menuItem -> {
                        if (menuItem.getItemId() == 1 && optionListener != null) {
                            optionListener.onCategoryDelete(item);
                            return true;
                        }
                        return false;
                    });
                    popup.show();
                });
            } else {
                binding.btnCategoryOptions.setVisibility(View.GONE);
            }

            binding.cardCategory.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onCategoryClicked(item);
                }
            });
        }
    }
}
