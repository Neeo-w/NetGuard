
package eu.faircode.netguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.p2p.WifiP2pDevice;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DeviceConnectionManager {
    private static final String TAG = "DeviceConnectionManager";
    private static final String PREF_NAME = "device_connections";
    private static final String PREF_ALLOWED_DEVICES = "allowed_devices";
    
    private static DeviceConnectionManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final Map<String, ConnectedDevice> connectedDevices;
    private final Map<String, Long> deviceTrafficMap;
    
    private DeviceConnectionManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.connectedDevices = new ConcurrentHashMap<>();
        this.deviceTrafficMap = new ConcurrentHashMap<>();
    }
    
    public static synchronized DeviceConnectionManager getInstance(Context context) {
        if (instance == null) {
            instance = new DeviceConnectionManager(context);
        }
        return instance;
    }
    
    public void addConnectedDevice(WifiP2pDevice device, String ipAddress) {
        String deviceId = device.deviceAddress;
        ConnectedDevice connectedDevice = new ConnectedDevice(
            device.deviceName != null ? device.deviceName : "Unknown Device",
            deviceId,
            ipAddress,
            System.currentTimeMillis()
        );
        
        connectedDevices.put(deviceId, connectedDevice);
        deviceTrafficMap.put(deviceId, 0L);
        
        Log.i(TAG, "Device connected: " + device.deviceName + " (" + ipAddress + ")");
        
        // Save to persistent storage
        saveConnectedDevices();
    }
    
    public void removeConnectedDevice(String deviceId) {
        ConnectedDevice removed = connectedDevices.remove(deviceId);
        deviceTrafficMap.remove(deviceId);
        
        if (removed != null) {
            Log.i(TAG, "Device disconnected: " + removed.getName());
            saveConnectedDevices();
        }
    }
    
    public List<ConnectedDevice> getConnectedDevices() {
        return new ArrayList<>(connectedDevices.values());
    }
    
    public ConnectedDevice getDeviceByIp(String ipAddress) {
        for (ConnectedDevice device : connectedDevices.values()) {
            if (device.getIpAddress().equals(ipAddress)) {
                return device;
            }
        }
        return null;
    }
    
    public void updateDeviceTraffic(String deviceId, long bytes) {
        Long currentTraffic = deviceTrafficMap.get(deviceId);
        if (currentTraffic != null) {
            deviceTrafficMap.put(deviceId, currentTraffic + bytes);
        }
    }
    
    public long getDeviceTraffic(String deviceId) {
        return deviceTrafficMap.getOrDefault(deviceId, 0L);
    }
    
    public boolean isDeviceAllowed(String deviceId) {
        String allowedDevices = prefs.getString(PREF_ALLOWED_DEVICES, "");
        return allowedDevices.contains(deviceId);
    }
    
    public void setDeviceAllowed(String deviceId, boolean allowed) {
        String allowedDevices = prefs.getString(PREF_ALLOWED_DEVICES, "");
        if (allowed && !allowedDevices.contains(deviceId)) {
            allowedDevices += deviceId + ",";
        } else if (!allowed) {
            allowedDevices = allowedDevices.replace(deviceId + ",", "");
        }
        prefs.edit().putString(PREF_ALLOWED_DEVICES, allowedDevices).apply();
    }
    
    private void saveConnectedDevices() {
        // Save current connected devices for persistence across app restarts
        StringBuilder deviceList = new StringBuilder();
        for (ConnectedDevice device : connectedDevices.values()) {
            deviceList.append(device.getDeviceId()).append(":").append(device.getIpAddress()).append(",");
        }
        prefs.edit().putString("connected_devices", deviceList.toString()).apply();
    }
    
    public void clearAllDevices() {
        connectedDevices.clear();
        deviceTrafficMap.clear();
        prefs.edit().clear().apply();
        Log.i(TAG, "All devices cleared");
    }
    
    public int getConnectedDeviceCount() {
        return connectedDevices.size();
    }
}
