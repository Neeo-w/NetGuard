
package eu.faircode.netguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.List;

public class ActivityTethering extends AppCompatActivity {
    private static final String TAG = "NetGuard.Tethering";
    
    private Switch swTetheringEnabled;
    private TextView tvStatus;
    private TextView tvPassword;
    private TextView tvProxyUrl;
    private Button btnGeneratePassword;
    private ListView lvConnectedDevices;
    
    private TetheringService tetheringService;
    private boolean isTetheringEnabled = false;
    private List<ConnectedDevice> connectedDevices;
    private ConnectedDeviceAdapter deviceAdapter;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tethering);
        
        getSupportActionBar().setTitle(R.string.title_tethering);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        
        initViews();
        setupEventHandlers();
        
        connectedDevices = new ArrayList<>();
        deviceAdapter = new ConnectedDeviceAdapter(this, connectedDevices);
        lvConnectedDevices.setAdapter(deviceAdapter);
        
        updateUI();
    }
    
    private void initViews() {
        swTetheringEnabled = findViewById(R.id.sw_tethering_enabled);
        tvStatus = findViewById(R.id.tv_status);
        tvPassword = findViewById(R.id.tv_password);
        tvProxyUrl = findViewById(R.id.tv_proxy_url);
        btnGeneratePassword = findViewById(R.id.btn_generate_password);
        lvConnectedDevices = findViewById(R.id.lv_connected_devices);
    }
    
    private void setupEventHandlers() {
        swTetheringEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                startTethering();
            } else {
                stopTethering();
            }
        });
        
        btnGeneratePassword.setOnClickListener(v -> generateNewPassword());
    }
    
    private void startTethering() {
        if (TetheringService.isSupported()) {
            Intent intent = new Intent(this, TetheringService.class);
            intent.setAction(TetheringService.ACTION_START);
            startService(intent);
            isTetheringEnabled = true;
            updateUI();
        } else {
            Toast.makeText(this, R.string.error_wifi_direct_not_supported, Toast.LENGTH_LONG).show();
            swTetheringEnabled.setChecked(false);
        }
    }
    
    private void stopTethering() {
        Intent intent = new Intent(this, TetheringService.class);
        intent.setAction(TetheringService.ACTION_STOP);
        startService(intent);
        isTetheringEnabled = false;
        connectedDevices.clear();
        deviceAdapter.notifyDataSetChanged();
        updateUI();
    }
    
    private void generateNewPassword() {
        String newPassword = TetheringService.generatePassword();
        tvPassword.setText(newPassword);
        // Update service with new password
        Intent intent = new Intent(this, TetheringService.class);
        intent.setAction(TetheringService.ACTION_UPDATE_PASSWORD);
        intent.putExtra("password", newPassword);
        startService(intent);
    }
    
    private void updateUI() {
        if (isTetheringEnabled) {
            tvStatus.setText(R.string.status_tethering_active);
            tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            tvProxyUrl.setText("http://192.168.49.1:8888");
            tvProxyUrl.setVisibility(View.VISIBLE);
        } else {
            tvStatus.setText(R.string.status_tethering_inactive);
            tvStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            tvProxyUrl.setVisibility(View.GONE);
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
    
    // Inner class for connected device info
    public static class ConnectedDevice {
        public String name;
        public String address;
        public long bytesTransferred;
        public boolean isActive;
        
        public ConnectedDevice(String name, String address) {
            this.name = name;
            this.address = address;
            this.bytesTransferred = 0;
            this.isActive = true;
        }
    }
}
