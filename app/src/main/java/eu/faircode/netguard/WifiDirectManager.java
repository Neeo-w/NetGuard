
package eu.faircode.netguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class WifiDirectManager {
    private static final String TAG = "NetGuard.WifiDirect";
    
    private Context context;
    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private WifiDirectBroadcastReceiver receiver;
    private WifiDirectListener listener;
    
    private boolean isGroupOwner = false;
    private WifiP2pGroup currentGroup;
    private List<WifiP2pDevice> connectedDevices;
    
    public interface WifiDirectListener {
        void onGroupCreated(WifiP2pGroup group);
        void onGroupRemoved();
        void onDeviceConnected(WifiP2pDevice device);
        void onDeviceDisconnected(WifiP2pDevice device);
        void onError(String error);
    }
    
    public WifiDirectManager(Context context, WifiDirectListener listener) {
        this.context = context;
        this.listener = listener;
        this.connectedDevices = new ArrayList<>();
        
        manager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(context, context.getMainLooper(), null);
        receiver = new WifiDirectBroadcastReceiver();
    }
    
    public void startGroupOwner(String groupName, String passphrase) {
        Log.i(TAG, "Starting Group Owner mode");
        
        // Create group configuration
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = "";
        config.groupOwnerIntent = 15; // High intent to become GO
        
        // Register broadcast receiver
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        context.registerReceiver(receiver, intentFilter);
        
        // Create group
        manager.createGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "Group created successfully");
                isGroupOwner = true;
            }
            
            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Failed to create group: " + reason);
                if (listener != null) {
                    listener.onError("Failed to create Wi-Fi Direct group");
                }
            }
        });
    }
    
    public void stopGroupOwner() {
        Log.i(TAG, "Stopping Group Owner mode");
        
        if (manager != null && channel != null) {
            manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.i(TAG, "Group removed successfully");
                    isGroupOwner = false;
                    connectedDevices.clear();
                    if (listener != null) {
                        listener.onGroupRemoved();
                    }
                }
                
                @Override
                public void onFailure(int reason) {
                    Log.e(TAG, "Failed to remove group: " + reason);
                }
            });
        }
        
        if (receiver != null) {
            try {
                context.unregisterReceiver(receiver);
            } catch (Exception e) {
                Log.w(TAG, "Receiver not registered: " + e.getMessage());
            }
        }
    }
    
    public boolean isGroupOwner() {
        return isGroupOwner;
    }
    
    public List<WifiP2pDevice> getConnectedDevices() {
        return new ArrayList<>(connectedDevices);
    }
    
    public static boolean isSupported(Context context) {
        return context.getPackageManager().hasSystemFeature("android.hardware.wifi.direct");
    }
    
    private class WifiDirectBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                Log.d(TAG, "Wi-Fi P2P state changed: " + state);
                
            } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                Log.d(TAG, "Peers changed");
                
            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                Log.d(TAG, "Connection changed");
                
                WifiP2pInfo p2pInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO);
                WifiP2pGroup group = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP);
                
                if (group != null) {
                    currentGroup = group;
                    Collection<WifiP2pDevice> devices = group.getClientList();
                    connectedDevices.clear();
                    connectedDevices.addAll(devices);
                    
                    Log.i(TAG, "Group info: " + group.getNetworkName() + 
                              ", devices: " + connectedDevices.size());
                    
                    if (listener != null) {
                        listener.onGroupCreated(group);
                    }
                }
                
            } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
                WifiP2pDevice device = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
                Log.d(TAG, "This device changed: " + device);
            }
        }
    }
}
