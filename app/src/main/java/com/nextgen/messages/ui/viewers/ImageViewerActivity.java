package com.nextgen.messages.ui.viewers;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.nextgen.messages.R;
import com.nextgen.messages.databinding.ActivityImageViewerBinding;
import com.nextgen.messages.utils.AppExecutors;
import java.io.InputStream;

/**
 * Full-screen image viewer for MMS photo attachments.
 */
public class ImageViewerActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_URI = "com.nextgen.messages.extra.IMAGE_URI";
    public static final String EXTRA_IMAGE_TITLE = "com.nextgen.messages.extra.IMAGE_TITLE";

    public static void launch(@NonNull android.content.Context context, @NonNull Uri imageUri, @Nullable String contentType, @Nullable String title) {
        android.content.Intent intent = new android.content.Intent(context, ImageViewerActivity.class);
        intent.putExtra(EXTRA_IMAGE_URI, imageUri.toString());
        if (title != null) {
            intent.putExtra(EXTRA_IMAGE_TITLE, title);
        }
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(intent);
    }

    private ActivityImageViewerBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        com.nextgen.messages.utils.ThemeHelper.applyToActivity(this);
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityImageViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        String title = getIntent().getStringExtra(EXTRA_IMAGE_TITLE);
        if (title != null && !title.isEmpty()) {
            binding.toolbar.setTitle(title);
        }

        String uriString = getIntent().getStringExtra(EXTRA_IMAGE_URI);
        if (uriString == null) {
            Toast.makeText(this, R.string.error_loading_attachment, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Uri imageUri = Uri.parse(uriString);
        loadImageAsync(imageUri);
    }

    private void loadImageAsync(Uri uri) {
        binding.progressImageLoading.setVisibility(View.VISIBLE);

        AppExecutors.getInstance().diskIO().execute(() -> {
            Bitmap bitmap = null;
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    if (is != null) {
                        BitmapFactory.decodeStream(is, null, options);
                    }
                }

                int reqWidth = 2048;
                int reqHeight = 2048;
                int inSampleSize = 1;
                if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                    final int halfHeight = options.outHeight / 2;
                    final int halfWidth = options.outWidth / 2;
                    while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                        inSampleSize *= 2;
                    }
                }

                options.inSampleSize = inSampleSize;
                options.inJustDecodeBounds = false;

                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    if (is != null) {
                        bitmap = BitmapFactory.decodeStream(is, null, options);
                    }
                }
            } catch (Exception ignored) {
            }

            final Bitmap resultBitmap = bitmap;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) return;
                binding.progressImageLoading.setVisibility(View.GONE);
                if (resultBitmap != null) {
                    binding.ivFullscreenImage.setImageBitmap(resultBitmap);
                } else {
                    Toast.makeText(ImageViewerActivity.this, R.string.error_loading_attachment, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}
