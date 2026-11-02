package com.nextgen.messages.smartinbox.db;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.nextgen.messages.utils.AppExecutors;
import java.util.ArrayList;
import java.util.List;

/**
 * Room Database for Smart Inbox metadata, classification rules, sender associations,
 * conversation metadata (pin/archive/block/spam), starred messages, and security warnings.
 */
@Database(
        entities = {
                MessageCategoryEntity.class,
                MessageClassificationEntity.class,
                ClassificationRuleEntity.class,
                SenderCategoryEntity.class,
                ConversationMetadataEntity.class,
                StarredMessageEntity.class,
                BlockedSenderEntity.class,
                SpamFeedbackEntity.class,
                SecurityWarningEntity.class
        },
        version = 3,
        exportSchema = false
)
public abstract class SmartInboxDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "nextgen_smart_inbox.db";
    private static volatile SmartInboxDatabase instance;

    public abstract SmartInboxDao smartInboxDao();

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // 1. conversation_metadata
            db.execSQL("CREATE TABLE IF NOT EXISTS `conversation_metadata` (" +
                    "`thread_id` INTEGER NOT NULL, " +
                    "`is_pinned` INTEGER NOT NULL, " +
                    "`pinned_at` INTEGER NOT NULL, " +
                    "`is_archived` INTEGER NOT NULL, " +
                    "`archived_at` INTEGER NOT NULL, " +
                    "`is_blocked` INTEGER NOT NULL, " +
                    "`is_spam` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`thread_id`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_metadata_is_pinned` ON `conversation_metadata` (`is_pinned`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_metadata_is_archived` ON `conversation_metadata` (`is_archived`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_metadata_is_blocked` ON `conversation_metadata` (`is_blocked`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_metadata_is_spam` ON `conversation_metadata` (`is_spam`)");

            // 2. starred_messages
            db.execSQL("CREATE TABLE IF NOT EXISTS `starred_messages` (" +
                    "`message_key` TEXT NOT NULL, " +
                    "`thread_id` INTEGER NOT NULL, " +
                    "`raw_message_id` INTEGER NOT NULL, " +
                    "`message_type` TEXT NOT NULL, " +
                    "`starred_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`message_key`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_starred_messages_thread_id` ON `starred_messages` (`thread_id`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_starred_messages_starred_at` ON `starred_messages` (`starred_at`)");

            // 3. blocked_senders
            db.execSQL("CREATE TABLE IF NOT EXISTS `blocked_senders` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`sender_identifier` TEXT NOT NULL, " +
                    "`normalized_identifier` TEXT NOT NULL, " +
                    "`blocked_at` INTEGER NOT NULL, " +
                    "`block_reason` TEXT)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_blocked_senders_normalized_identifier` ON `blocked_senders` (`normalized_identifier`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_blocked_senders_blocked_at` ON `blocked_senders` (`blocked_at`)");

            // 4. spam_feedback
            db.execSQL("CREATE TABLE IF NOT EXISTS `spam_feedback` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`sender_identifier` TEXT NOT NULL, " +
                    "`normalized_identifier` TEXT NOT NULL, " +
                    "`thread_id` INTEGER NOT NULL, " +
                    "`message_key` TEXT, " +
                    "`feedback_type` TEXT NOT NULL, " +
                    "`reported_at` INTEGER NOT NULL)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_spam_feedback_normalized_identifier` ON `spam_feedback` (`normalized_identifier`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_spam_feedback_feedback_type` ON `spam_feedback` (`feedback_type`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_spam_feedback_reported_at` ON `spam_feedback` (`reported_at`)");

            // 5. security_warnings
            db.execSQL("CREATE TABLE IF NOT EXISTS `security_warnings` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`message_key` TEXT NOT NULL, " +
                    "`thread_id` INTEGER NOT NULL, " +
                    "`detected_url` TEXT NOT NULL, " +
                    "`warning_type` TEXT NOT NULL, " +
                    "`warning_reason` TEXT NOT NULL, " +
                    "`is_dismissed` INTEGER NOT NULL, " +
                    "`detected_at` INTEGER NOT NULL)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_security_warnings_message_key` ON `security_warnings` (`message_key`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_security_warnings_thread_id` ON `security_warnings` (`thread_id`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_security_warnings_detected_at` ON `security_warnings` (`detected_at`)");
        }
    };

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `conversation_metadata` ADD COLUMN `muted_until` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `conversation_metadata` ADD COLUMN `is_silent` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `conversation_metadata` ADD COLUMN `custom_sound_uri` TEXT");
            db.execSQL("ALTER TABLE `conversation_metadata` ADD COLUMN `notification_id` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `conversation_metadata` ADD COLUMN `last_notified_message_key` TEXT");
            db.execSQL("ALTER TABLE `conversation_metadata` ADD COLUMN `last_notification_timestamp` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `conversation_metadata` ADD COLUMN `is_dismissed` INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static SmartInboxDatabase getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (SmartInboxDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            SmartInboxDatabase.class,
                            DATABASE_NAME
                    )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigrationOnDowngrade()
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
