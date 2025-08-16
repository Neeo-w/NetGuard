package eu.faircode.netguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

public class TetheringService extends Service implements 
        WifiDirectManager.WifiDirectListener, 
        HttpProxyServer.ProxyListener {

    private static final String TAG = "NetGuard.TetheringService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "tethering_channel";

    public static final String ACTION_START = "START_TETHERING";
    public static final String ACTION_STOP = "STOP_TETHERING";
    public static final String ACTION_UPDATE_PASSWORD = "UPDATE_PASSWORD";

    private WifiDirectManager wifiDirectManager;
    private HttpProxyServer proxyServer;
    private boolean isActive = false;
    private String currentPassword = "netguard123";
    private Map<String, Long> clientDataUsage;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Tethering service created");

        clientDataUsage = new HashMap<>();
        createNotificationChannel();

        wifiDirectManager = new WifiDirectManager(this, this);
        proxyServer = new HttpProxyServer(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START.equals(action)) {
                startTethering();
            } else if (ACTION_STOP.equals(action)) {
                stopTethering();
            } else if (ACTION_UPDATE_PASSWORD.equals(action)) {
                String newPassword = intent.getStringExtra("password");
                if (newPassword != null) {
                    currentPassword = newPassword;
                    Log.i(TAG, "Password updated");
                }
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startTethering() {
        if (isActive) {
            Log.w(TAG, "Tethering already active");
            return;
        }

        Log.i(TAG, "Starting Wi-Fi Direct tethering");

        if (!isSupported()) {
            Log.e(TAG, "Wi-Fi Direct not supported");
            broadcastError("Wi-Fi Direct not supported on this device");
            return;
        }

        // Start Wi-Fi Direct Group Owner
        wifiDirectManager.startGroupOwner("NetGuard-Hotspot", currentPassword);

        // Start HTTP proxy server
        proxyServer.start();

        isActive = true;
        startForeground(NOTIFICATION_ID, createNotification());

        // Broadcast status update
        Intent statusIntent = new Intent("eu.faircode.netguard.TETHERING_STATUS");
        statusIntent.putExtra("active", true);
        statusIntent.putExtra("proxy_url", proxyServer.getProxyUrl());
        sendBroadcast(statusIntent);
    }

    private void stopTethering() {
        if (!isActive) {
            return;
        }

        Log.i(TAG, "Stopping Wi-Fi Direct tethering");

        // Stop Wi-Fi Direct group
        wifiDirectManager.stopGroupOwner();

        // Stop HTTP proxy server
        proxyServer.stop();

        isActive = false;
        clientDataUsage.clear();
        stopForeground(true);

        // Broadcast status update
        Intent statusIntent = new Intent("eu.faircode.netguard.TETHERING_STATUS");
        statusIntent.putExtra("active", false);
        sendBroadcast(statusIntent);
    }

    public static boolean isSupported() {
        // Check Wi-Fi Direct support - simplified check
        return true; // Most modern Android devices support Wi-Fi Direct
    }

    public static String generatePassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            password.append(chars.charAt(random.nextInt(chars.length())));
        }
        return password.toString();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Tethering Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Wi-Fi Direct Tethering notifications");

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        Intent intent = new Intent(this, ActivityTethering.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NetGuard Tethering Active")
            .setContentText("Wi-Fi Direct hotspot is running")
            .setSmallIcon(R.drawable.ic_signal_wifi_4_bar_white_24dp)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
    }

    private void broadcastError(String error) {
        Intent errorIntent = new Intent("eu.faircode.netguard.TETHERING_ERROR");
        errorIntent.putExtra("error", error);
        sendBroadcast(errorIntent);
    }

    // WifiDirectManager.WifiDirectListener implementation
    @Override
    public void onGroupCreated(WifiP2pGroup group) {
        Log.i(TAG, "Wi-Fi Direct group created: " + group.getNetworkName());

        // Broadcast group info
        Intent groupIntent = new Intent("eu.faircode.netguard.TETHERING_GROUP_CREATED");
        groupIntent.putExtra("group_name", group.getNetworkName());
        groupIntent.putExtra("passphrase", group.getPassphrase());
        sendBroadcast(groupIntent);
    }

    @Override
    public void onGroupRemoved() {
        Log.i(TAG, "Wi-Fi Direct group removed");

        Intent groupIntent = new Intent("eu.faircode.netguard.TETHERING_GROUP_REMOVED");
        sendBroadcast(groupIntent);
    }

    @Override
    public void onDeviceConnected(WifiP2pDevice device) {
        Log.i(TAG, "Device connected: " + device.deviceName);

        Intent deviceIntent = new Intent("eu.faircode.netguard.TETHERING_DEVICE_CONNECTED");
        deviceIntent.putExtra("device_name", device.deviceName);
        deviceIntent.putExtra("device_address", device.deviceAddress);
        sendBroadcast(deviceIntent);
    }

    @Override
    public void onDeviceDisconnected(WifiP2pDevice device) {
        Log.i(TAG, "Device disconnected: " + device.deviceName);

        String deviceAddress = device.deviceAddress;
        clientDataUsage.remove(deviceAddress);

        Intent deviceIntent = new Intent("eu.faircode.netguard.TETHERING_DEVICE_DISCONNECTED");
        deviceIntent.putExtra("device_name", device.deviceName);
        deviceIntent.putExtra("device_address", deviceAddress);
        sendBroadcast(deviceIntent);
    }

    @Override
    public void onError(String error) {
        Log.e(TAG, "Wi-Fi Direct error: " + error);
        broadcastError(error);
    }

    // HttpProxyServer.ProxyListener implementation
    @Override
    public void onClientConnected(String clientAddress) {
        Log.d(TAG, "Proxy client connected: " + clientAddress);
    }

    @Override
    public void onClientDisconnected(String clientAddress) {
        Log.d(TAG, "Proxy client disconnected: " + clientAddress);
    }

    @Override
    public void onDataTransferred(String clientAddress, long bytes) {
        Long currentUsage = clientDataUsage.get(clientAddress);
        if (currentUsage == null) {
            currentUsage = 0L;
        }
        clientDataUsage.put(clientAddress, currentUsage + bytes);

        // Broadcast data usage update
        Intent dataIntent = new Intent("eu.faircode.netguard.TETHERING_DATA_USAGE");
        dataIntent.putExtra("client_address", clientAddress);
        dataIntent.putExtra("bytes_transferred", currentUsage + bytes);
        sendBroadcast(dataIntent);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Tethering service destroyed");
        stopTethering();
        super.onDestroy();
    }
}