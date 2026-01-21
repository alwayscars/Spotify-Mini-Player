package com.example.spotifyminiplayer;

import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.List;
import java.util.Set;

public class SpotifyNotificationListener extends NotificationListenerService {

    public static String songName = "No song";
    public static String artistName = "Unknown";

    private static final String TAG = "SpotifyListener";

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d(TAG, "✅ Notification Listener Connected!");

        // Try to get current playing info immediately
        updateFromMediaController();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Log.d(TAG, "📬 Notification from: " + sbn.getPackageName());

        if (!"com.spotify.music".equals(sbn.getPackageName())) return;

        Log.d(TAG, "🎵 Spotify notification detected!");

        // Method 1: Try MediaController first (most reliable)
        if (updateFromMediaController()) {
            return; // Success!
        }

        // Method 2: Try notification extras
        if (sbn.getNotification() == null || sbn.getNotification().extras == null) {
            Log.e(TAG, "❌ Notification or extras is null");
            return;
        }

        Bundle extras = sbn.getNotification().extras;

        // Log ALL keys to see what's available
        Set<String> keys = extras.keySet();
        Log.d(TAG, "📋 Available keys: " + keys);

        for (String key : keys) {
            Object value = extras.get(key);
            Log.d(TAG, "  " + key + " = " + value);
        }

        // Try various common keys
        String title = getStringFromExtras(extras, "android.title");
        String text = getStringFromExtras(extras, "android.text");
        String subText = getStringFromExtras(extras, "android.subText");
        String bigText = getStringFromExtras(extras, "android.bigText");
        String infoText = getStringFromExtras(extras, "android.infoText");

        boolean updated = false;

        if (title != null && !title.isEmpty()) {
            songName = title;
            Log.d(TAG, "✅ Song: " + songName);
            updated = true;
        }

        if (text != null && !text.isEmpty()) {
            artistName = text;
            Log.d(TAG, "✅ Artist: " + artistName);
            updated = true;
        } else if (subText != null && !subText.isEmpty()) {
            artistName = subText;
            Log.d(TAG, "✅ Artist (from subText): " + artistName);
            updated = true;
        }

        if (updated) {
            notifyActivity();
        } else {
            Log.e(TAG, "❌ Could not extract song/artist info");
        }
    }

    private String getStringFromExtras(Bundle extras, String key) {
        try {
            CharSequence cs = extras.getCharSequence(key);
            if (cs != null) {
                return cs.toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting key: " + key, e);
        }
        return null;
    }

    private boolean updateFromMediaController() {
        try {
            MediaSessionManager mediaSessionManager =
                    (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);

            if (mediaSessionManager == null) {
                Log.e(TAG, "❌ MediaSessionManager is null");
                return false;
            }

            List<MediaController> controllers =
                    mediaSessionManager.getActiveSessions(
                            new android.content.ComponentName(this, SpotifyNotificationListener.class));

            Log.d(TAG, "🎮 Active media sessions: " + controllers.size());

            for (MediaController controller : controllers) {
                String pkg = controller.getPackageName();
                Log.d(TAG, "  Session from: " + pkg);

                if ("com.spotify.music".equals(pkg)) {
                    MediaMetadata metadata = controller.getMetadata();

                    if (metadata == null) {
                        Log.e(TAG, "❌ Spotify metadata is null");
                        continue;
                    }

                    // Log all available metadata keys
                    Set<String> keys = metadata.keySet();
                    Log.d(TAG, "📋 Metadata keys: " + keys);

                    String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
                    String artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
                    String album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM);

                    Log.d(TAG, "  Title: " + title);
                    Log.d(TAG, "  Artist: " + artist);
                    Log.d(TAG, "  Album: " + album);

                    boolean updated = false;

                    if (title != null && !title.isEmpty()) {
                        songName = title;
                        updated = true;
                    }

                    if (artist != null && !artist.isEmpty()) {
                        artistName = artist;
                        updated = true;
                    }

                    if (updated) {
                        Log.d(TAG, "✅ Updated from MediaController!");
                        notifyActivity();
                        return true;
                    }
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "❌ SecurityException - notification access not granted?", e);
        } catch (Exception e) {
            Log.e(TAG, "❌ Error in updateFromMediaController", e);
        }

        return false;
    }

    private void notifyActivity() {
        Intent intent = new Intent("SPOTIFY_SONG_UPDATE");
        sendBroadcast(intent);
        Log.d(TAG, "📡 Broadcast sent to activity");
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if ("com.spotify.music".equals(sbn.getPackageName())) {
            Log.d(TAG, "🛑 Spotify notification removed");
            songName = "No song";
            artistName = "Stopped";
            notifyActivity();
        }
    }
}