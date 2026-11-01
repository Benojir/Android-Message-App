package com.nextgen.messages.ui.categories;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.nextgen.messages.data.model.CategoryItem;
import com.nextgen.messages.databinding.ItemCategoryCardBinding;
import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for displaying planned smart categories in Material Design 3 cards.
 */
public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    public interface OnCategoryClickListener {
        void onCategoryClicked(CategoryItem item);
    }

    private final List<CategoryItem> items = new ArrayList<>();
    private final OnCategoryClickListener listener;

    public CategoryAdapter(OnCategoryClickListener listener) {
        this.listener = listener;
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
        holder.bind(items.get(position), listener);
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

        void bind(CategoryItem item, OnCategoryClickListener listener) {
            Context context = itemView.getContext();

            binding.tvCategoryTitle.setText(item.getTitleRes());
            binding.tvCategoryDesc.setText(item.getDescRes());
            binding.tvCategoryBadge.setText(item.getBadgeRes());
            binding.ivCategoryIcon.setImageResource(item.getIconRes());

            int iconColor = ContextCompat.getColor(context, item.getColorRes());
            binding.ivCategoryIcon.setImageTintList(ColorStateList.valueOf(iconColor));

            int containerColor = ContextCompat.getColor(context, item.getContainerColorRes());
            binding.layoutIconContainer.setBackgroundTintList(ColorStateList.valueOf(containerColor));

            binding.cardCategory.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCategoryClicked(item);
                }
            });
        }
    }
}
