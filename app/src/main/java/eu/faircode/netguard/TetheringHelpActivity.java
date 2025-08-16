
package eu.faircode.netguard;

import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class TetheringHelpActivity extends AppCompatActivity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tethering_help);
        
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Tethering Help");
        
        WebView webView = findViewById(R.id.webview_help);
        webView.setWebViewClient(new WebViewClient());
        
        String helpContent = generateHelpContent();
        webView.loadDataWithBaseURL(null, helpContent, "text/html", "UTF-8", null);
    }
    
    private String generateHelpContent() {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'><title>NetGuard Tethering Guide</title>" +
               "<style>body{font-family:Arial,sans-serif;padding:20px;background:#f5f5f5;}" +
               "h1{color:#1976D2;}h2{color:#424242;}p{line-height:1.5;color:#666;}" +
               ".step{background:white;padding:15px;margin:10px 0;border-radius:8px;box-shadow:0 2px 4px rgba(0,0,0,0.1);}" +
               ".warning{background:#fff3cd;border:1px solid #ffeaa7;padding:10px;border-radius:5px;margin:10px 0;}" +
               "</style></head><body>" +
               
               "<h1>NetGuard Wi-Fi Direct Tethering Guide</h1>" +
               "<p>Share your NetGuard-protected internet connection with other devices securely.</p>" +
               
               "<div class='warning'><strong>Important:</strong> This feature creates a Wi-Fi Direct connection. " +
               "Other devices will connect directly to your phone as a hotspot.</div>" +
               
               "<h2>How to Set Up Tethering</h2>" +
               
               "<div class='step'>" +
               "<h3>Step 1: Enable Tethering</h3>" +
               "<p>1. Open NetGuard main menu (⋮)<br>" +
               "2. Select 'Wi-Fi Direct Tethering'<br>" +
               "3. Tap 'Start Tethering' button<br>" +
               "4. Your device will become a Wi-Fi Direct Group Owner</p>" +
               "</div>" +
               
               "<div class='step'>" +
               "<h3>Step 2: Connect External Devices</h3>" +
               "<p>On the device you want to connect:<br>" +
               "1. Go to Wi-Fi settings<br>" +
               "2. Look for 'Wi-Fi Direct' or 'Direct Connect'<br>" +
               "3. Find your NetGuard device in the list<br>" +
               "4. Connect to it</p>" +
               "</div>" +
               
               "<div class='step'>" +
               "<h3>Step 3: Configure Proxy Settings</h3>" +
               "<p>On the connected device:<br>" +
               "1. Go to Wi-Fi settings<br>" +
               "2. Find the connected NetGuard network<br>" +
               "3. Tap 'Advanced' or 'Modify Network'<br>" +
               "4. Set Proxy to 'Manual'<br>" +
               "5. Proxy hostname: <strong>192.168.49.1</strong><br>" +
               "6. Proxy port: <strong>8888</strong><br>" +
               "7. Save settings</p>" +
               "</div>" +
               
               "<h2>Supported Services</h2>" +
               "<p>✅ Web browsing (HTTP/HTTPS)<br>" +
               "✅ YouTube and video streaming<br>" +
               "✅ Social media apps<br>" +
               "✅ Email and messaging<br>" +
               "✅ App downloads and updates</p>" +
               
               "<h2>Security Features</h2>" +
               "<p>🔒 <strong>Device Authorization:</strong> Only approved devices can connect<br>" +
               "🔒 <strong>Traffic Filtering:</strong> NetGuard rules apply to all traffic<br>" +
               "🔒 <strong>Encrypted Connection:</strong> HTTPS proxy with self-signed certificates<br>" +
               "🔒 <strong>Traffic Monitoring:</strong> View data usage per connected device</p>" +
               
               "<h2>Troubleshooting</h2>" +
               
               "<div class='step'>" +
               "<h3>Connection Issues</h3>" +
               "<p>• Ensure Wi-Fi Direct is enabled on both devices<br>" +
               "• Check that NetGuard has location permissions<br>" +
               "• Restart Wi-Fi on both devices<br>" +
               "• Make sure devices are within 30 feet of each other</p>" +
               "</div>" +
               
               "<div class='step'>" +
               "<h3>Internet Not Working</h3>" +
               "<p>• Verify proxy settings: 192.168.49.1:8888<br>" +
               "• Check that NetGuard VPN is active<br>" +
               "• Ensure the device is authorized in NetGuard<br>" +
               "• Try disconnecting and reconnecting</p>" +
               "</div>" +
               
               "<div class='step'>" +
               "<h3>YouTube/Streaming Issues</h3>" +
               "<p>• Some apps may need to be restarted after connecting<br>" +
               "• Clear app cache if videos won't load<br>" +
               "• Check NetGuard rules allow the streaming apps<br>" +
               "• Ensure proxy is set to 'Use for all connections'</p>" +
               "</div>" +
               
               "<h2>Battery and Performance</h2>" +
               "<p>Tethering uses additional battery power. To optimize:<br>" +
               "• Limit connected devices to what you need<br>" +
               "• Disable tethering when not in use<br>" +
               "• Monitor data usage in the Connected Devices section</p>" +
               
               "</body></html>";
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
