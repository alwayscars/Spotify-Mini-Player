package com.example.spotifyminiplayer;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Bitmap;
import android.widget.ImageView;
import android.media.MediaMetadata;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    TextView songText, artistText;
    MediaController spotifyController;
    Button playPause;
    Handler refreshHandler;
    Runnable refreshRunnable;
    private int lastPlaybackState = -1;
    ImageView albumArt;
    TextView currentTime, totalTime;
    android.widget.SeekBar progressBar;
    private boolean userIsSeeking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enable fullscreen mode and hide status bar
        getWindow().getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN |
                        android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        // Hide action bar if it exists
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_main);

        songText = findViewById(R.id.songText);
        artistText = findViewById(R.id.artistText);
        albumArt = findViewById(R.id.albumArt);
        currentTime = findViewById(R.id.currentTime);
        totalTime = findViewById(R.id.totalTime);
        progressBar = findViewById(R.id.progressBar);

        playPause = findViewById(R.id.playPauseBtn);
        Button next = findViewById(R.id.nextBtn);
        Button prev = findViewById(R.id.prevBtn);

        // Enable seeking on progress bar
        progressBar.setEnabled(true);
        progressBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    currentTime.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {
                userIsSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {
                userIsSeeking = false;
                if (spotifyController != null) {
                    try {
                        spotifyController.getTransportControls().seekTo(seekBar.getProgress());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        // Display current values immediately
        updateUI();

        // Auto-refresh on startup
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            updateUI();
            findSpotifySession();
        }, 500);

        // Setup auto-refresh every second
        refreshHandler = new Handler(Looper.getMainLooper());
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                updateUI();
                if (spotifyController == null) {
                    findSpotifySession();
                }
                refreshHandler.postDelayed(this, 1000); // Run every 1 second
            }
        };
        refreshHandler.post(refreshRunnable);

        // Check Notification Access
        if (!hasNotificationAccess()) {
            Toast.makeText(
                    this,
                    "Please enable Notification Access for this app",
                    Toast.LENGTH_LONG
            ).show();

            startActivity(new Intent(
                    Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } else {
            // Find Spotify session on startup
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                findSpotifySession();
            }, 1000);
        }

        // Register receiver FIRST before anything else
        IntentFilter filter = new IntentFilter("SPOTIFY_SONG_UPDATE");
        registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);

        playPause.setOnClickListener(v -> {
            if (spotifyController != null) {
                try {
                    PlaybackState state = spotifyController.getPlaybackState();
                    if (state != null) {
                        if (state.getState() == PlaybackState.STATE_PLAYING) {
                            spotifyController.getTransportControls().pause();
                            playPause.setText("▶");
                        } else {
                            spotifyController.getTransportControls().play();
                            playPause.setText("⏸");
                        }
                    } else {
                        spotifyController.getTransportControls().play();
                        playPause.setText("⏸");
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "Failed to control playback", Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }
            } else {
                Toast.makeText(this, "Spotify not connected", Toast.LENGTH_SHORT).show();
                findSpotifySession();
            }
        });

        next.setOnClickListener(v -> {
            if (spotifyController != null) {
                try {
                    spotifyController.getTransportControls().skipToNext();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        prev.setOnClickListener(v -> {
            if (spotifyController != null) {
                try {
                    spotifyController.getTransportControls().skipToPrevious();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private boolean hasNotificationAccess() {
        String enabledListeners = Settings.Secure.getString(
                getContentResolver(),
                "enabled_notification_listeners"
        );
        return enabledListeners != null &&
                enabledListeners.contains(getPackageName());
    }

    private void findSpotifySession() {
        try {
            MediaSessionManager manager =
                    (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);

            if (manager == null) {
                return;
            }

            List<MediaController> sessions =
                    manager.getActiveSessions(
                            new ComponentName(this, SpotifyNotificationListener.class));

            spotifyController = null;
            for (MediaController controller : sessions) {
                if ("com.spotify.music".equals(controller.getPackageName())) {
                    spotifyController = controller;
                    break;
                }
            }

        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d("MainActivity", "📡 Broadcast received!");
            Log.d("MainActivity", "Song: " + SpotifyNotificationListener.songName);
            Log.d("MainActivity", "Artist: " + SpotifyNotificationListener.artistName);

            updateUI();

            if (spotifyController == null) {
                findSpotifySession();
            }
        }
    };

    private void updateUI() {
        songText.setText(SpotifyNotificationListener.songName);
        artistText.setText(SpotifyNotificationListener.artistName);

        // Update album art, progress, and button state
        if (spotifyController != null) {
            try {
                MediaMetadata metadata = spotifyController.getMetadata();
                if (metadata != null) {
                    Bitmap artwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                    if (artwork != null) {
                        albumArt.setImageBitmap(artwork);
                    }

                    // Get song duration
                    long duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
                    if (duration > 0) {
                        totalTime.setText(formatTime(duration));
                        progressBar.setMax((int) duration);
                    }
                }

                PlaybackState state = spotifyController.getPlaybackState();
                if (state != null) {
                    // Update progress only if user is not seeking
                    if (!userIsSeeking) {
                        long position = state.getPosition();
                        currentTime.setText(formatTime(position));
                        progressBar.setProgress((int) position);
                    }

                    int currentState = state.getState();

                    // Only update button if state changed
                    if (currentState != lastPlaybackState) {
                        lastPlaybackState = currentState;

                        if (currentState == PlaybackState.STATE_PLAYING) {
                            playPause.setText("⏸");
                            playPause.setTextColor(0xFF000000);
                        } else {
                            playPause.setText("▶");
                            playPause.setTextColor(0xFF000000);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private String formatTime(long millis) {
        int seconds = (int) (millis / 1000);
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Try to find Spotify session when app resumes
        if (hasNotificationAccess()) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                findSpotifySession();
            }, 500);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Stop the refresh handler
        if (refreshHandler != null && refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }

        try {
            unregisterReceiver(updateReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}