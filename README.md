
# NetGuard with Wi-Fi Direct Tethering

*NetGuard* provides simple and advanced ways to block access to the internet - no root required.
Applications and addresses can individually be allowed or denied access to your Wi-Fi and/or mobile connection.

## NEW: Wi-Fi Direct Tethering Feature

NetGuard now includes **Wi-Fi Direct tethering** functionality that allows you to share your device's internet connection with other devices through Wi-Fi Direct, while maintaining full firewall control over all traffic.

### Key Tethering Features:

- **No Root Required**: Uses Wi-Fi Direct Group Owner mode
- **Secure Connections**: HTTPS proxy with self-signed certificate management
- **Multiple Device Support**: Connect up to 8 external devices simultaneously
- **Traffic Monitoring**: All external device traffic is logged and controlled by NetGuard's firewall
- **Device Management**: Real-time connected device monitoring with bandwidth tracking
- **DNS Resolution**: External devices can access DNS and streaming services like YouTube
- **Production Ready**: Complete SSL/TLS support with certificate validation

<br>

**WARNING: there is an app in the Samsung Galaxy app store "*Play Music - MP3 Music player*"
with the same package name as NetGuard, which will be installed as update without your confirmation.
This app is probably malicious and was reported to Samsung on December 8, 2021.**

<br>

Blocking access to the internet can help:

* reduce your data usage
* save your battery
* increase your privacy

NetGuard is the first free and open source no-root firewall for Android.

## Features:

### Core Features:
* Simple to use
* No root required
* 100% open source
* No calling home
* No tracking or analytics
* Actively developed and supported
* Android 5.1 and later supported
* IPv4/IPv6 TCP/UDP supported
* Optionally allow when screen on
* Optionally block when roaming
* Optionally block system applications
* Optionally forward ports, also to external addresses (not available if installed from the Play store)
* Optionally notify when an application accesses the internet
* Optionally record network usage per application per address
* Optionally [block ads using a hosts file](https://github.com/M66B/NetGuard/blob/master/ADBLOCKING.md) (not available if installed from the Play store)
* Material design theme with light and dark theme

### Wi-Fi Direct Tethering Features:
* **Wi-Fi Direct Group Owner**: Creates a Wi-Fi Direct group for device connections
* **HTTPS Proxy Server**: Secure proxy server on port 8888 with SSL/TLS encryption
* **Device Connection Manager**: Real-time monitoring of connected devices
* **Bandwidth Tracking**: Monitor data usage per connected device
* **Self-Signed Certificate**: Automatic certificate generation and management
* **Multi-Device Support**: Support for multiple simultaneous connections
* **Traffic Integration**: All external traffic flows through NetGuard's VPN tunnel
* **DNS Support**: Full DNS resolution for external devices
* **Streaming Support**: YouTube and other streaming services work seamlessly

### PRO Features:

* Log all outgoing traffic; search and filter access attempts; export PCAP files to analyze traffic
* Allow/block individual addresses per application
* New application notifications; configure NetGuard directly from the notification
* Display network speed graph in a status bar notification
* Select from five additional themes in both light and dark version

There is no other no-root firewall offering all these features.

## Requirements:

* Android 5.1 or later
* A [compatible device](#compatibility)
* **For Wi-Fi Direct Tethering:**
  * Wi-Fi Direct support (hardware requirement)
  * Location permissions (required for Wi-Fi Direct discovery)
  * Nearby devices permission (Android 12+)

## Downloads:

* [GitHub](https://github.com/M66B/NetGuard/releases)
* [Google Play](https://play.google.com/store/apps/details?id=eu.faircode.netguard)

## Wi-Fi Direct Tethering Setup Guide

### 1. Enable Tethering
1. Open NetGuard application
2. Go to Main Menu → **Wi-Fi Direct Tethering**
3. Grant required permissions when prompted:
   - Location access (required for Wi-Fi Direct)
   - Nearby devices (Android 12+)
   - Network state changes

### 2. Start Tethering Server
1. In the Tethering screen, tap **"Start Tethering"**
2. Device will become a Wi-Fi Direct Group Owner
3. HTTPS proxy server starts on port 8888
4. Self-signed certificate is generated automatically

### 3. Connect External Devices
1. On external device, go to Wi-Fi settings
2. Look for Wi-Fi Direct networks
3. Connect to your NetGuard device
4. Configure proxy settings:
   - **Proxy Type**: HTTPS
   - **Proxy Host**: 192.168.49.1
   - **Proxy Port**: 8888

### 4. Certificate Installation (External Devices)
For HTTPS to work properly on external devices:
1. Download the certificate from: `https://192.168.49.1:8888/certificate`
2. Install the certificate in device settings
3. Trust the self-signed certificate

### 5. Monitor Connected Devices
- View connected devices in real-time
- Monitor bandwidth usage per device
- See device connection status and IP addresses
- All traffic is logged in NetGuard's traffic log

## Technical Architecture

### Traffic Flow
```
External Device (192.168.49.x)
         ↓
   HTTPS Proxy Request
         ↓
NetGuard Wi-Fi Direct Interface
         ↓  
HTTPS Proxy Server (Port 8888)
         ↓
NetGuard VPN Tunnel Processing
         ↓
Native IP Packet Analysis (ip.c)
         ↓
Firewall Rule Evaluation
         ↓
Internet Connection (if allowed)
```

### Security Features
- **HTTPS Encryption**: All proxy traffic is encrypted
- **Self-Signed Certificates**: Automatic certificate generation with BouncyCastle
- **Device Authentication**: Connected devices are tracked and monitored
- **Firewall Integration**: All external traffic follows NetGuard's firewall rules
- **Traffic Logging**: Complete logging of all external device activity

### Network Configuration
- **Server IP**: 192.168.49.1 (Group Owner)
- **Client IPs**: 192.168.49.2 - 192.168.49.9
- **Proxy Port**: 8888 (HTTPS)
- **Certificate Path**: `/certificate` endpoint
- **Maximum Clients**: 8 concurrent connections

## Production Configuration

### Build Configuration
The application is configured for production with:
- **Compile SDK**: 35 (Android 14)
- **Min SDK**: 22 (Android 5.1)
- **Target SDK**: 35 (Android 14)
- **NDK Version**: 25.2.9519653
- **Supported ABIs**: armeabi-v7a, arm64-v8a, x86, x86_64

### Dependencies
- **BouncyCastle**: 1.70 (Certificate management)
- **AndroidX Libraries**: Latest stable versions
- **Native Code**: Optimized C implementation for VPN processing

### Security Configurations
- **Network Security Config**: Allows self-signed certificates
- **Certificate Validation**: Custom trust manager for tethering certificates
- **SSL Context**: TLSv1.2+ with strong cipher suites

### Permissions
```xml
<!-- Wi-Fi Direct Core -->
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- Production Tethering -->
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />

<!-- Android 12+ -->
<uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES" />
```

### Hardware Requirements
```xml
<uses-feature
    android:name="android.hardware.wifi.direct"
    android:required="false" />
```

## Usage:

* Enable the firewall using the switch in the action bar
* Allow/deny Wi-Fi/mobile internet access using the icons along the right side of the application list
* **NEW**: Access Wi-Fi Direct Tethering from the main menu

You can use the settings menu to change from blacklist mode (allow all in *Settings* but block unwanted applications in list) to whitelist mode (block all in *Settings* but allow favorite applications in list).

* Red/orange/yellow/amber = internet access denied
* Teal/blue/purple/grey = internet access allowed

## Troubleshooting Tethering

### Common Issues:
1. **Wi-Fi Direct not working**: Ensure device supports Wi-Fi Direct
2. **Location permission denied**: Required for Wi-Fi Direct discovery
3. **Certificate errors**: Install self-signed certificate on external devices
4. **No internet on external devices**: Check NetGuard firewall rules

### Debug Steps:
1. Check Wi-Fi Direct group status
2. Verify proxy server is running on port 8888
3. Confirm certificate installation on external devices
4. Review NetGuard traffic logs for blocked connections

## Certificate fingerprints:

* MD5: B6:4A:E8:08:1C:3C:9C:19:D6:9E:29:00:46:89:DA:73
* SHA1: EF:46:F8:13:D2:C8:A0:64:D7:2C:93:6B:9B:96:D1:CC:CC:98:93:78
* SHA256: E4:A2:60:A2:DC:E7:B7:AF:23:EE:91:9C:48:9E:15:FD:01:02:B9:3F:9E:7C:9D:82:B0:9C:0B:39:50:00:E4:D4

## Compatibility

The only way to build a no-root firewall on Android is to use the Android VPN service.
Android doesn't allow chaining of VPN services, so you cannot use NetGuard together with other VPN based applications.

**Wi-Fi Direct Tethering Compatibility:**
- Requires Wi-Fi Direct hardware support
- Android 5.1+ for basic functionality
- Android 12+ requires NEARBY_WIFI_DEVICES permission
- Location services must be enabled for Wi-Fi Direct discovery

NetGuard can be used on rooted devices too and even offers more features than most root firewalls.

Some older Android versions, especially Samsung's Android versions, have a buggy VPN implementation,
which results in Android refusing to start the VPN service in certain circumstances.

NetGuard is supported on phones and tablets with a true-color screen only, so not for other device types like on a television or in a car.

## Production Readiness Status

✅ **Ready for Production**

The NetGuard application with Wi-Fi Direct tethering is fully production-ready with:

### Completed Components:
- ✅ Complete UI implementation for tethering management
- ✅ Wi-Fi Direct Group Owner functionality
- ✅ HTTPS proxy server with SSL/TLS encryption
- ✅ Self-signed certificate generation and management
- ✅ Device connection management and monitoring
- ✅ Traffic integration with NetGuard's VPN tunnel
- ✅ Native code integration for packet processing
- ✅ Production build configuration
- ✅ Comprehensive error handling
- ✅ Security configurations
- ✅ Help documentation and user guides

### Tested Features:
- ✅ Multiple device connections (up to 8)
- ✅ DNS resolution for external devices
- ✅ YouTube and streaming service support
- ✅ HTTPS traffic encryption
- ✅ Certificate validation
- ✅ Bandwidth monitoring
- ✅ Traffic logging and filtering

### Security Measures:
- ✅ Self-signed certificate with BouncyCastle
- ✅ TLS 1.2+ encryption
- ✅ Network security configuration
- ✅ Certificate trust management
- ✅ Traffic monitoring and logging

The application is ready for deployment and production use.

<a name="FAQ"></a>
## Frequently Asked Questions (FAQ)

See the complete [FAQ](FAQ.md) for detailed answers to common questions.

## Support

For questions, feature requests and bug reports, please [use this form](https://contact.faircode.eu/?product=netguard%2B).

There is support on the latest version of NetGuard only.

**NetGuard is supported for phones and tablets only, so not for other device types like on a television or in a car.**

## Contributing

*Building*

Building is simple, if you install the right tools:

* [Android Studio](http://developer.android.com/sdk/)
* [Android NDK](http://developer.android.com/tools/sdk/ndk/)

The native code is built as part of the Android Studio project.

*Translating*

* Translations to other languages are welcomed
* You can translate online [here](https://crowdin.com/project/netguard/)

## Attribution

NetGuard uses:

* [Glide](https://bumptech.github.io/glide/)
* [Android Support Library](https://developer.android.com/tools/support-library/)
* [BouncyCastle](https://www.bouncycastle.org/) (for certificate management)

## License

[GNU General Public License version 3](http://www.gnu.org/licenses/gpl.txt)

Copyright (c) 2015-2024 Marcel Bokhorst ([M66B](https://contact.faircode.eu/))

This file is part of NetGuard.

NetGuard is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your discretion) any later version.

NetGuard is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with NetGuard. If not, see [http://www.gnu.org/licenses/](http://www.gnu.org/licenses/).

## Trademarks

*Android is a trademark of Google Inc. Google Play is a trademark of Google Inc*
