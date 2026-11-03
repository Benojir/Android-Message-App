package com.nextgen.messages.ui.viewers;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.MediaController;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.nextgen.messages.R;
import com.nextgen.messages.databinding.ActivityVideoViewerBinding;

/**
 * Full-screen video player for MMS video attachments using Android's native VideoView.
 */
public class VideoViewerActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_URI = "com.nextgen.messages.extra.VIDEO_URI";
    public static final String EXTRA_VIDEO_TITLE = "com.nextgen.messages.extra.VIDEO_TITLE";

    public static void launch(@NonNull android.content.Context context, @NonNull Uri videoUri, @Nullable String title) {
        android.content.Intent intent = new android.content.Intent(context, VideoViewerActivity.class);
        intent.putExtra(EXTRA_VIDEO_URI, videoUri.toString());
        if (title != null) {
            intent.putExtra(EXTRA_VIDEO_TITLE, title);
        }
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(intent);
    }

    private ActivityVideoViewerBinding binding;
    private int currentPosition = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        com.nextgen.messages.utils.ThemeHelper.applyToActivity(this);
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityVideoViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        String title = getIntent().getStringExtra(EXTRA_VIDEO_TITLE);
        if (title != null && !title.isEmpty()) {
            binding.toolbar.setTitle(title);
        }

        String uriString = getIntent().getStringExtra(EXTRA_VIDEO_URI);
        if (uriString == null) {
            Toast.makeText(this, R.string.error_loading_attachment, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Uri videoUri = Uri.parse(uriString);
        setupVideoView(videoUri);
    }

    private void setupVideoView(Uri videoUri) {
        binding.progressVideoLoading.setVisibility(View.VISIBLE);

        MediaController mediaController = new MediaController(this);
        mediaController.setAnchorView(binding.videoView);
        binding.videoView.setMediaController(mediaController);
        binding.videoView.setVideoURI(videoUri);

        binding.videoView.setOnPreparedListener(mp -> {
            binding.progressVideoLoading.setVisibility(View.GONE);
            if (currentPosition > 0) {
                binding.videoView.seekTo(currentPosition);
            }
            binding.videoView.start();
        });

        binding.videoView.setOnErrorListener((mp, what, extra) -> {
            binding.progressVideoLoading.setVisibility(View.GONE);
            Toast.makeText(VideoViewerActivity.this, R.string.error_loading_attachment, Toast.LENGTH_SHORT).show();
            return true;
        });

        binding.videoView.setOnCompletionListener(mp -> {
            // Finished playing
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (binding.videoView.isPlaying()) {
            currentPosition = binding.videoView.getCurrentPosition();
            binding.videoView.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding.videoView.stopPlayback();
    }
}
