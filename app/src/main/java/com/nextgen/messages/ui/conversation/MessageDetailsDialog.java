package com.nextgen.messages.ui.conversation;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.nextgen.messages.R;
import com.nextgen.messages.data.model.ContactInfo;
import com.nextgen.messages.data.model.SmsMessage;
import com.nextgen.messages.databinding.DialogMessageDetailsBinding;
import com.nextgen.messages.smartinbox.SmartInboxRepository;
import com.nextgen.messages.smartinbox.db.MessageCategoryEntity;
import com.nextgen.messages.smartinbox.db.MessageClassificationEntity;
import com.nextgen.messages.smartinbox.engine.ClassificationResult;
import com.nextgen.messages.telephony.SimInfo;
import com.nextgen.messages.telephony.SimManager;
import com.nextgen.messages.telephony.SmsEncodingUtils;
import com.nextgen.messages.telephony.SmsSentReceiver;
import com.nextgen.messages.utils.AppExecutors;
import com.nextgen.messages.utils.DateFormatter;
import java.util.List;

/**
 * Material 3 dialog presenting real Telephony Provider metadata
 * and Smart Inbox classification transparency (category, source, and explanation).
 */
public final class MessageDetailsDialog {

    private MessageDetailsDialog() {}

    public static void show(
            @NonNull Context context,
            @NonNull SmsMessage message,
            @Nullable ContactInfo contactInfo
    ) {
        DialogMessageDetailsBinding binding = DialogMessageDetailsBinding.inflate(LayoutInflater.from(context));

        // 1. Message Type
        String typeString;
        if (message.isMms()) {
            if (message.isGroupMms()) {
                typeString = "Group MMS";
            } else if (message.isIncoming()) {
                typeString = "MMS (" + context.getString(R.string.details_type_incoming) + ")";
            } else if (message.isFailed()) {
                typeString = "MMS (" + context.getString(R.string.details_type_outgoing_failed) + ")";
            } else if (message.isOutbox()) {
                typeString = "MMS (" + context.getString(R.string.details_type_outgoing_outbox) + ")";
            } else {
                typeString = "MMS (" + context.getString(R.string.details_type_outgoing_sent) + ")";
            }
        } else {
            if (message.isIncoming()) {
                typeString = context.getString(R.string.details_type_incoming);
            } else if (message.isFailed()) {
                typeString = context.getString(R.string.details_type_outgoing_failed);
            } else if (message.isOutbox()) {
                typeString = context.getString(R.string.details_type_outgoing_outbox);
            } else if (message.isDraft()) {
                typeString = context.getString(R.string.details_type_draft);
            } else {
                typeString = context.getString(R.string.details_type_outgoing_sent);
            }
        }
        binding.tvDetailsType.setText(typeString);

        // 2. Address / Contact (From / To)
        String contactDisplay = null;
        if (contactInfo != null && contactInfo.hasName()) {
            contactDisplay = contactInfo.getDisplayName() + " (" + message.getDisplayAddress() + ")";
        } else {
            contactDisplay = message.getDisplayAddress();
        }

        if (message.isIncoming()) {
            binding.tvDetailsAddressLabel.setText(context.getString(R.string.details_label_from));
        } else {
            binding.tvDetailsAddressLabel.setText(context.getString(R.string.details_label_to));
        }
        binding.tvDetailsAddress.setText(contactDisplay);

        // 3. Received Date
        if (message.getDate() > 0) {
            binding.tvDetailsReceivedDate.setText(DateFormatter.formatFullDateTime(message.getDate()));
            binding.tvDetailsReceivedLabel.setVisibility(View.VISIBLE);
            binding.tvDetailsReceivedDate.setVisibility(View.VISIBLE);
        } else {
            binding.tvDetailsReceivedLabel.setVisibility(View.GONE);
            binding.tvDetailsReceivedDate.setVisibility(View.GONE);
        }

        // 4. Sent Date (only shown if available from provider)
        if (message.getDateSent() > 0) {
            binding.tvDetailsSentDate.setText(DateFormatter.formatFullDateTime(message.getDateSent()));
            binding.tvDetailsSentLabel.setVisibility(View.VISIBLE);
            binding.tvDetailsSentDate.setVisibility(View.VISIBLE);
        } else {
            binding.tvDetailsSentLabel.setVisibility(View.GONE);
            binding.tvDetailsSentDate.setVisibility(View.GONE);
        }

        // 5. SIM Subscription
        SimManager simManager = SimManager.getInstance(context);
        SimInfo simInfo = (message.getSubscriptionId() > 0) ? simManager.getSimForSubscriptionId(message.getSubscriptionId()) : null;
        if (simInfo != null) {
            binding.tvDetailsSim.setText(simInfo.getDisplayName() + " (" + simInfo.getCarrierName() + ")");
        } else if (message.getSubscriptionId() > 0) {
            binding.tvDetailsSim.setText("Subscription ID: " + message.getSubscriptionId());
        } else {
            binding.tvDetailsSim.setText(context.getString(R.string.not_available));
        }

        // 6. Delivery / Transmission Status
        String statusText;
        if (message.isMms() && message.isNotificationInd()) {
            if (message.getMmsDownloadState() == SmsMessage.DOWNLOAD_STATE_DOWNLOADING) {
                statusText = "Downloading multimedia payload...";
            } else if (message.getMmsDownloadState() == SmsMessage.DOWNLOAD_STATE_FAILED) {
                statusText = "Download failed (tap to retry)";
            } else {
                statusText = "Pending manual download";
            }
        } else if (message.isFailed()) {
            String errorDetail = (message.getErrorCode() != 0) ? SmsSentReceiver.getErrorMessage(message.getErrorCode()) : "Transmission error";
            statusText = context.getString(R.string.details_status_failed, errorDetail);
        } else if (message.isDelivered()) {
            statusText = context.getString(R.string.details_status_delivered);
        } else if (message.isSent()) {
            statusText = context.getString(R.string.details_status_sent);
        } else if (message.isOutbox()) {
            statusText = context.getString(R.string.details_status_pending);
        } else {
            statusText = context.getString(R.string.not_available);
        }
        binding.tvDetailsStatus.setText(statusText);

        // 7. Message Length & Encoding Segments / MMS Payload
        if (message.isMms()) {
            StringBuilder sb = new StringBuilder();
            if (message.getMessageSize() > 0) {
                sb.append(android.text.format.Formatter.formatFileSize(context, message.getMessageSize()));
            }
            if (message.hasAttachments()) {
                if (sb.length() > 0) sb.append(" • ");
                sb.append(context.getString(R.string.mms_attachment_count, message.getAttachments().size()));
            }
            if (message.getSubject() != null && !message.getSubject().trim().isEmpty()) {
                if (sb.length() > 0) sb.append("\nSubject: ");
                sb.append(message.getSubject().trim());
            }
            if (sb.length() == 0) {
                sb.append("MMS Message");
            }
            binding.tvDetailsSize.setText(sb.toString());
        } else {
            String body = message.getDisplayBody();
            int charCount = body.length();
            SmsEncodingUtils.SegmentInfo segmentInfo = SmsEncodingUtils.calculateSegments(body);
            int segmentCount = segmentInfo.getSegmentCount();
            String segmentWord = (segmentCount == 1) ? context.getString(R.string.segment_single) : context.getString(R.string.segment_plural);
            String encodingName = segmentInfo.isUnicode() ? "Unicode (UCS-2)" : "Standard (GSM 7-bit)";
            String sizeFormatted = context.getString(R.string.details_size_format, charCount, segmentCount, segmentWord, encodingName);
            binding.tvDetailsSize.setText(sizeFormatted);
        }

        // 8. Smart Inbox Classification & Transparency
        SmartInboxRepository smartRepo = SmartInboxRepository.getInstance(context);
        loadClassificationDetails(context, message, smartRepo, binding);

        binding.btnChangeCategory.setOnClickListener(v -> {
            showChangeCategoryDialog(context, message, smartRepo, binding);
        });

        // Build and display dialog
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.dialog_details_title)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.dialog_button_close, null)
                .show();
    }

    private static void loadClassificationDetails(
            @NonNull Context context,
            @NonNull SmsMessage message,
            @NonNull SmartInboxRepository smartRepo,
            @NonNull DialogMessageDetailsBinding binding
    ) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            String messageKey = MessageClassificationEntity.buildMessageKey(message.isMms(), message.getId());
            MessageClassificationEntity ce = smartRepo.getClassificationForMessage(messageKey);
            if (ce == null) {
                smartRepo.classifyMessageSync(message);
                ce = smartRepo.getClassificationForMessage(messageKey);
            }

            final MessageClassificationEntity finalCe = ce;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (finalCe != null) {
                    binding.tvDetailsCategory.setText(getLocalizedCategoryName(context, finalCe.getCategoryKey()));
                    String srcText;
                    if (MessageClassificationEntity.SOURCE_MANUAL.equals(finalCe.getClassificationSource())) {
                        srcText = "Manual Override";
                    } else if (MessageClassificationEntity.SOURCE_RULE.equals(finalCe.getClassificationSource())) {
                        srcText = "User Custom Rule";
                    } else {
                        srcText = "Automatic Heuristics (" + (int)(finalCe.getConfidence() * 100) + "% confidence)";
                    }
                    binding.tvDetailsCategorySource.setText(srcText);
                    binding.tvDetailsCategoryReason.setText(finalCe.getReason());
                } else {
                    binding.tvDetailsCategory.setText(context.getString(R.string.category_other_title));
                    binding.tvDetailsCategorySource.setText("None");
                    binding.tvDetailsCategoryReason.setText("Unclassified");
                }
            });
        });
    }

    private static void showChangeCategoryDialog(
            @NonNull Context context,
            @NonNull SmsMessage message,
            @NonNull SmartInboxRepository smartRepo,
            @NonNull DialogMessageDetailsBinding binding
    ) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            List<MessageCategoryEntity> categories = smartRepo.getAllCategoriesSync();
            // Filter out "all" category
            categories.removeIf(c -> "all".equals(c.getCategoryKey()));

            CharSequence[] items = new CharSequence[categories.size()];
            for (int i = 0; i < categories.size(); i++) {
                items[i] = getLocalizedCategoryName(context, categories.get(i).getCategoryKey());
            }

            AppExecutors.getInstance().mainThread().execute(() -> {
                final int[] selectedIndex = {0};

                View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_assign_category, null, false);
                CheckBox cbAlways = dialogView.findViewById(R.id.cb_always_sender);
                cbAlways.setText(context.getString(R.string.checkbox_always_sender, message.getDisplayAddress()));

                new MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.dialog_change_category_title)
                        .setSingleChoiceItems(items, 0, (dialog, which) -> {
                            selectedIndex[0] = which;
                        })
                        .setView(dialogView)
                        .setPositiveButton(R.string.dialog_button_ok, (dialog, which) -> {
                            int chosen = selectedIndex[0];
                            if (chosen >= 0 && chosen < categories.size()) {
                                MessageCategoryEntity targetCategory = categories.get(chosen);
                                String targetKey = targetCategory.getCategoryKey();

                                smartRepo.setManualMessageCategory(
                                        message.getThreadId(),
                                        message.getId(),
                                        message.isMms(),
                                        targetKey,
                                        () -> {
                                            binding.tvDetailsCategory.setText(getLocalizedCategoryName(context, targetKey));
                                            binding.tvDetailsCategorySource.setText("Manual Override");
                                            binding.tvDetailsCategoryReason.setText("User manual category override");
                                            Toast.makeText(context, R.string.toast_category_updated, Toast.LENGTH_SHORT).show();
                                        }
                                );

                                if (cbAlways.isChecked() && message.getAddress() != null) {
                                    smartRepo.setManualSenderCategory(message.getAddress(), targetKey, null);
                                }
                            }
                        })
                        .setNegativeButton(R.string.dialog_button_cancel, null)
                        .show();
            });
        });
    }

    public static String getLocalizedCategoryName(@NonNull Context context, @Nullable String categoryKey) {
        if (categoryKey == null) return context.getString(R.string.category_other_title);
        switch (categoryKey) {
            case "all": return context.getString(R.string.category_all_title);
            case "personal": return context.getString(R.string.category_personal_title);
            case "transactions": return context.getString(R.string.category_transactions_title);
            case "otp": return context.getString(R.string.category_otp_title);
            case "promotions": return context.getString(R.string.category_promotions_title);
            case "business": return context.getString(R.string.category_business_title);
            case "spam": return context.getString(R.string.category_spam_title);
            case "other": return context.getString(R.string.category_other_title);
            default:
                if (categoryKey.startsWith("custom_")) {
                    String clean = categoryKey.substring("custom_".length()).replace('_', ' ');
                    return clean.substring(0, 1).toUpperCase() + clean.substring(1);
                }
                return categoryKey.substring(0, 1).toUpperCase() + categoryKey.substring(1);
        }
    }
}
