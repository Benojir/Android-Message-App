package com.nextgen.messages.smartinbox.db;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.nextgen.messages.utils.AppExecutors;
import java.util.ArrayList;
import java.util.List;

/**
 * Room Database for Smart Inbox metadata, classification rules, and sender associations.
 */
@Database(
        entities = {
                MessageCategoryEntity.class,
                MessageClassificationEntity.class,
                ClassificationRuleEntity.class,
                SenderCategoryEntity.class
        },
        version = 1,
        exportSchema = false
)
public abstract class SmartInboxDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "nextgen_smart_inbox.db";
    private static volatile SmartInboxDatabase instance;

    public abstract SmartInboxDao smartInboxDao();

    public static SmartInboxDatabase getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (SmartInboxDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            SmartInboxDatabase.class,
                            DATABASE_NAME
                    )
                    .addCallback(new Callback() {
                        @Override
                        public void onCreate(@NonNull SupportSQLiteDatabase db) {
                            super.onCreate(db);
                            // Pre-populate built-in categories
                            AppExecutors.getInstance().diskIO().execute(() -> {
                                SmartInboxDao dao = getInstance(context).smartInboxDao();
                                dao.insertCategories(getInitialBuiltInCategories());
                            });
                        }

                        @Override
                        public void onOpen(@NonNull SupportSQLiteDatabase db) {
                            super.onOpen(db);
                            // Ensure built-in categories exist
                            AppExecutors.getInstance().diskIO().execute(() -> {
                                SmartInboxDao dao = getInstance(context).smartInboxDao();
                                dao.insertCategories(getInitialBuiltInCategories());
                            });
                        }
                    })
                    .build();
                }
            }
        }
        return instance;
    }

    @NonNull
    public static List<MessageCategoryEntity> getInitialBuiltInCategories() {
        List<MessageCategoryEntity> list = new ArrayList<>();
        list.add(MessageCategoryEntity.createBuiltIn("all", "All Messages", "Every conversation in your inbox", "ALL", "ic_sms", "#0D57D0", 0));
        list.add(MessageCategoryEntity.createBuiltIn("personal", "Personal", "Messages from personal contacts", "PERSONAL", "ic_category_personal", "#00875A", 1));
        list.add(MessageCategoryEntity.createBuiltIn("transactions", "Transactions", "Bank credits, debits, UPI and financial alerts", "TRANSACTIONS", "ic_category_transactions", "#00838F", 2));
        list.add(MessageCategoryEntity.createBuiltIn("otp", "OTP", "One-time passwords and verification codes", "OTP", "ic_category_otp", "#E65100", 3));
        list.add(MessageCategoryEntity.createBuiltIn("promotions", "Promotions", "Offers, sales, discounts and coupons", "PROMOTIONS", "ic_category_promotions", "#7B1FA2", 4));
        list.add(MessageCategoryEntity.createBuiltIn("business", "Business", "Service updates, orders, deliveries and appointments", "BUSINESS", "ic_info", "#1565C0", 5));
        list.add(MessageCategoryEntity.createBuiltIn("spam", "Spam", "Suspicious or potentially unwanted messages", "SPAM", "ic_category_spam", "#C62828", 6));
        list.add(MessageCategoryEntity.createBuiltIn("other", "Other", "Messages not confidently classified into other categories", "OTHER", "ic_info", "#546E7A", 7));
        return list;
    }
}
