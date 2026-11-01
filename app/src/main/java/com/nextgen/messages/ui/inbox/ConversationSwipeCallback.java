package com.nextgen.messages.ui.inbox;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.nextgen.messages.R;
import com.nextgen.messages.data.model.Conversation;

/**
 * ItemTouchHelper callback providing smooth swipe gestures on inbox conversation items:
 * - Swipe Right: Toggle Mark as Read / Unread with visual icon feedback.
 * - Swipe Left: Delete conversation with confirmation.
 * Disabled while multi-selection mode is active.
 */
public class ConversationSwipeCallback extends ItemTouchHelper.SimpleCallback {

    public interface SwipeListener {
        void onSwipeToggleRead(int position, @NonNull Conversation conversation);
        void onSwipeDelete(int position, @NonNull Conversation conversation);
    }

    private final ConversationAdapter adapter;
    private final SwipeListener listener;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable deleteIcon;
    private final Drawable readIcon;
    private final Drawable unreadIcon;

    public ConversationSwipeCallback(
            @NonNull Context context,
            @NonNull ConversationAdapter adapter,
            @NonNull SwipeListener listener
    ) {
        super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        this.adapter = adapter;
        this.listener = listener;

        deleteIcon = ContextCompat.getDrawable(context, R.drawable.ic_delete);
        readIcon = ContextCompat.getDrawable(context, R.drawable.ic_mark_chat_read);
        unreadIcon = ContextCompat.getDrawable(context, R.drawable.ic_mark_chat_unread);

        if (deleteIcon != null) {
            deleteIcon.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        }
        if (readIcon != null) {
            readIcon.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        }
        if (unreadIcon != null) {
            unreadIcon.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        }
    }

    @Override
    public int getSwipeDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        if (adapter.isInSelectionMode()) {
            return 0; // Disable swipe gestures during selection mode
        }
        return super.getSwipeDirs(recyclerView, viewHolder);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        int position = viewHolder.getBindingAdapterPosition();
        if (position < 0 || position >= adapter.getItemCount()) {
            return;
        }

        Conversation item = adapter.getCurrentList().get(position);
        if (direction == ItemTouchHelper.RIGHT) {
            listener.onSwipeToggleRead(position, item);
        } else if (direction == ItemTouchHelper.LEFT) {
            listener.onSwipeDelete(position, item);
        }
    }

    @Override
    public void onChildDraw(
            @NonNull Canvas c,
            @NonNull RecyclerView recyclerView,
            @NonNull RecyclerView.ViewHolder viewHolder,
            float dX,
            float dY,
            int actionState,
            boolean isCurrentlyActive
    ) {
        View itemView = viewHolder.itemView;
        int itemHeight = itemView.getBottom() - itemView.getTop();

        if (dX > 0) {
            // Swiping Right -> Mark Read/Unread
            int position = viewHolder.getBindingAdapterPosition();
            boolean isUnread = false;
            if (position >= 0 && position < adapter.getItemCount()) {
                isUnread = adapter.getCurrentList().get(position).hasUnread();
            }

            bgPaint.setColor(Color.parseColor("#1976D2")); // Blue accent
            c.drawRect((float) itemView.getLeft(), (float) itemView.getTop(), dX, (float) itemView.getBottom(), bgPaint);

            Drawable icon = isUnread ? readIcon : unreadIcon;
            if (icon != null) {
                int iconMargin = (itemHeight - icon.getIntrinsicHeight()) / 2;
                int iconTop = itemView.getTop() + iconMargin;
                int iconBottom = iconTop + icon.getIntrinsicHeight();
                int iconLeft = itemView.getLeft() + iconMargin;
                int iconRight = iconLeft + icon.getIntrinsicWidth();

                if (dX > iconRight) {
                    icon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                    icon.draw(c);
                }
            }
        } else if (dX < 0) {
            // Swiping Left -> Delete
            bgPaint.setColor(Color.parseColor("#D32F2F")); // Red error
            c.drawRect((float) itemView.getRight() + dX, (float) itemView.getTop(), (float) itemView.getRight(), (float) itemView.getBottom(), bgPaint);

            if (deleteIcon != null) {
                int iconMargin = (itemHeight - deleteIcon.getIntrinsicHeight()) / 2;
                int iconTop = itemView.getTop() + iconMargin;
                int iconBottom = iconTop + deleteIcon.getIntrinsicHeight();
                int iconRight = itemView.getRight() - iconMargin;
                int iconLeft = iconRight - deleteIcon.getIntrinsicWidth();

                if (dX < -(itemView.getRight() - iconLeft)) {
                    deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                    deleteIcon.draw(c);
                }
            }
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }
}
