package com.nextgen.messages.ui.categories;

import android.app.Application;
import android.graphics.Color;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.nextgen.messages.R;
import com.nextgen.messages.data.model.CategoryItem;
import com.nextgen.messages.smartinbox.SmartInboxRepository;
import com.nextgen.messages.smartinbox.db.MessageCategoryEntity;
import com.nextgen.messages.utils.AppExecutors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * ViewModel managing Smart Inbox category state, Room synchronization,
 * message and unread counts, and custom category lifecycle operations.
 */
public class CategoriesViewModel extends AndroidViewModel {

    private final SmartInboxRepository smartInboxRepository;
    private final MutableLiveData<List<CategoryItem>> categoriesLiveData = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    public CategoriesViewModel(@NonNull Application application) {
        super(application);
        this.smartInboxRepository = SmartInboxRepository.getInstance(application);
        loadCategories();
    }

    public LiveData<List<CategoryItem>> getCategories() {
        return categoriesLiveData;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public void loadCategories() {
        isLoading.setValue(true);
        AppExecutors.getInstance().diskIO().execute(() -> {
            List<MessageCategoryEntity> entities = smartInboxRepository.getAllCategoriesSync();
            Map<String, SmartInboxRepository.CategoryCounts> countsMap = smartInboxRepository.getCategoryCountsSync();

            List<CategoryItem> items = new ArrayList<>();

            for (MessageCategoryEntity entity : entities) {
                String key = entity.getCategoryKey();
                SmartInboxRepository.CategoryCounts counts = countsMap.get(key);

                int msgCount = counts != null ? counts.messageCount : 0;
                int unreadCount = counts != null ? counts.unreadCount : 0;
                int convCount = counts != null ? counts.conversationCount : 0;

                CategoryItem item = mapEntityToCategoryItem(entity);
                item.setMessageCount(msgCount);
                item.setUnreadCount(unreadCount);
                item.setConversationCount(convCount);
                items.add(item);
            }

            AppExecutors.getInstance().mainThread().execute(() -> {
                categoriesLiveData.setValue(items);
                isLoading.setValue(false);
            });
        });
    }

    private CategoryItem mapEntityToCategoryItem(MessageCategoryEntity entity) {
        String key = entity.getCategoryKey();
        switch (key) {
            case "all":
                return new CategoryItem("all", R.string.category_all_title, R.string.category_all_desc,
                        R.drawable.ic_category_all, 0, R.color.cat_all, R.color.cat_all_container);
            case "personal":
                return new CategoryItem("personal", R.string.category_personal_title, R.string.category_personal_desc,
                        R.drawable.ic_category_personal, 0, R.color.cat_personal, R.color.cat_personal_container);
            case "transactions":
                return new CategoryItem("transactions", R.string.category_transactions_title, R.string.category_transactions_desc,
                        R.drawable.ic_category_transactions, 0, R.color.cat_transactions, R.color.cat_transactions_container);
            case "otp":
                return new CategoryItem("otp", R.string.category_otp_title, R.string.category_otp_desc,
                        R.drawable.ic_category_otp, 0, R.color.cat_otp, R.color.cat_otp_container);
            case "promotions":
                return new CategoryItem("promotions", R.string.category_promotions_title, R.string.category_promotions_desc,
                        R.drawable.ic_category_promotions, 0, R.color.cat_promotions, R.color.cat_promotions_container);
            case "business":
                return new CategoryItem("business", R.string.category_business_title, R.string.category_business_desc,
                        R.drawable.ic_category_business, 0, R.color.cat_business, R.color.cat_business_container);
            case "spam":
                return new CategoryItem("spam", R.string.category_spam_title, R.string.category_spam_desc,
                        R.drawable.ic_category_spam, 0, R.color.cat_spam, R.color.cat_spam_container);
            case "other":
                return new CategoryItem("other", R.string.category_other_title, R.string.category_other_desc,
                        R.drawable.ic_category_other, 0, R.color.cat_other, R.color.cat_other_container);
            default:
                int parsedColor;
                try {
                    parsedColor = Color.parseColor(entity.getColorHex());
                } catch (Exception e) {
                    parsedColor = Color.parseColor("#546E7A");
                }
                int containerColor = Color.argb(38, Color.red(parsedColor), Color.green(parsedColor), Color.blue(parsedColor));
                return new CategoryItem(
                        key,
                        entity.getDisplayName(),
                        "User-defined custom category",
                        R.drawable.ic_category_other,
                        parsedColor,
                        containerColor,
                        false
                );
        }
    }

    public void createCustomCategory(@NonNull String name, @NonNull String colorHex) {
        smartInboxRepository.createCustomCategory(name, "ic_category_other", colorHex, this::loadCategories);
    }

    public void deleteCustomCategory(@NonNull String categoryKey) {
        smartInboxRepository.deleteCustomCategory(categoryKey, this::loadCategories);
    }
}
