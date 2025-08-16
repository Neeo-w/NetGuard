
package eu.faircode.netguard;

import android.content.Context;
import android.util.Log;
import java.io.*;
import java.security.*;
import java.security.cert.*;
import java.security.spec.RSAKeyGenParameterSpec;
import javax.net.ssl.*;
import java.math.BigInteger;
import java.util.Date;
import javax.security.auth.x500.X500Principal;

public class SelfSignedCertificateManager {
    private static final String TAG = "NetGuard.Certificate";
    private static final String KEYSTORE_ALIAS = "netguard_proxy";
    private static final String KEYSTORE_PASSWORD = "netguard123";
    
    private Context context;
    private KeyStore keyStore;
    private SSLContext sslContext;
    
    public SelfSignedCertificateManager(Context context) {
        this.context = context;
        initializeKeyStore();
    }
    
    private void initializeKeyStore() {
        try {
            keyStore = KeyStore.getInstance("PKCS12");
            
            File keystoreFile = new File(context.getFilesDir(), "netguard_keystore.p12");
            
            if (keystoreFile.exists()) {
                // Load existing keystore
                try (FileInputStream fis = new FileInputStream(keystoreFile)) {
                    keyStore.load(fis, KEYSTORE_PASSWORD.toCharArray());
                }
                Log.i(TAG, "Loaded existing keystore");
            } else {
                // Create new keystore with self-signed certificate
                keyStore.load(null, null);
                generateSelfSignedCertificate();
                
                // Save keystore
                try (FileOutputStream fos = new FileOutputStream(keystoreFile)) {
                    keyStore.store(fos, KEYSTORE_PASSWORD.toCharArray());
                }
                Log.i(TAG, "Created new keystore with self-signed certificate");
            }
            
            // Initialize SSL context
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, KEYSTORE_PASSWORD.toCharArray());
            
            // Trust all certificates (for proxy usage)
            TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                }
            };
            
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), trustAllCerts, new SecureRandom());
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing keystore", e);
        }
    }
    
    private void generateSelfSignedCertificate() throws Exception {
        // Generate key pair
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4));
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        
        // Create self-signed certificate
        long now = System.currentTimeMillis();
        Date startDate = new Date(now);
        Date endDate = new Date(now + (365L * 24 * 60 * 60 * 1000)); // 1 year
        
        X500Principal dnName = new X500Principal("CN=NetGuard Proxy, O=NetGuard, C=US");
        
        // This is a simplified version - in production you'd use BouncyCastle
        // For now, we'll create a basic certificate
        X509Certificate certificate = createSelfSignedCertificate(keyPair, dnName, startDate, endDate);
        
        // Store in keystore
        keyStore.setKeyEntry(KEYSTORE_ALIAS, keyPair.getPrivate(), 
                           KEYSTORE_PASSWORD.toCharArray(), 
                           new Certificate[]{certificate});
    }
    
    private X509Certificate createSelfSignedCertificate(KeyPair keyPair, X500Principal dnName, 
                                                      Date startDate, Date endDate) throws Exception {
        // This is a simplified implementation
        // In production, use BouncyCastle or similar library
        
        // For now, we'll use a workaround with the Android keystore system
        // This would need proper X.509 certificate generation
        
        // Create a dummy certificate for demonstration
        // In real implementation, use proper certificate generation libraries
        throw new UnsupportedOperationException("Implement proper certificate generation with BouncyCastle");
    }
    
    public SSLContext getSSLContext() {
        return sslContext;
    }
    
    public SSLServerSocketFactory getServerSocketFactory() {
        return sslContext.getServerSocketFactory();
    }
    
    public String getCertificateFingerprint() {
        try {
            Certificate cert = keyStore.getCertificate(KEYSTORE_ALIAS);
            if (cert instanceof X509Certificate) {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] der = cert.getEncoded();
                md.update(der);
                byte[] digest = md.finish();
                
                StringBuilder sb = new StringBuilder();
                for (byte b : digest) {
                    sb.append(String.format("%02X:", b));
                }
                return sb.substring(0, sb.length() - 1);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting certificate fingerprint", e);
        }
        return "Unknown";
    }
}
