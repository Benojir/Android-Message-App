package com.nextgen.messages.data.repository;

import com.nextgen.messages.R;
import com.nextgen.messages.data.model.CategoryItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Repository providing the planned smart message categories.
 */
public class CategoryRepository {

    private static volatile CategoryRepository instance;

    private CategoryRepository() {}

    public static CategoryRepository getInstance() {
        if (instance == null) {
            synchronized (CategoryRepository.class) {
                if (instance == null) {
                    instance = new CategoryRepository();
                }
            }
        }
        return instance;
    }

    public List<CategoryItem> getCategories() {
        List<CategoryItem> list = new ArrayList<>();
        list.add(new CategoryItem(
                "personal",
                R.string.category_personal_title,
                R.string.category_personal_desc,
                R.drawable.ic_category_personal,
                R.string.category_badge_phase3,
                R.color.cat_personal,
                R.color.cat_personal_container
        ));
        list.add(new CategoryItem(
                "transactions",
                R.string.category_transactions_title,
                R.string.category_transactions_desc,
                R.drawable.ic_category_transactions,
                R.string.category_badge_phase3,
                R.color.cat_transactions,
                R.color.cat_transactions_container
        ));
        list.add(new CategoryItem(
                "otp",
                R.string.category_otp_title,
                R.string.category_otp_desc,
                R.drawable.ic_category_otp,
                R.string.category_badge_phase3,
                R.color.cat_otp,
                R.color.cat_otp_container
        ));
        list.add(new CategoryItem(
                "promotions",
                R.string.category_promotions_title,
                R.string.category_promotions_desc,
                R.drawable.ic_category_promotions,
                R.string.category_badge_phase3,
                R.color.cat_promotions,
                R.color.cat_promotions_container
        ));
        list.add(new CategoryItem(
                "spam",
                R.string.category_spam_title,
                R.string.category_spam_desc,
                R.drawable.ic_category_spam,
                R.string.category_badge_phase4,
                R.color.cat_spam,
                R.color.cat_spam_container
        ));
        return Collections.unmodifiableList(list);
    }
}
