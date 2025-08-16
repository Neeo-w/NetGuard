package eu.faircode.netguard;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.HashMap;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HttpProxyServer {
    private static final String TAG = "NetGuard.HttpProxy";
    private static final int PROXY_PORT = 8888;

    private Context context;
    private ProxyListener listener;
    private ServerSocket serverSocket;
    private boolean isRunning = false;
    private Thread serverThread;
    private Map<String, ClientSession> clientSessions;

    public interface ProxyListener {
        void onClientConnected(String clientAddress);
        void onClientDisconnected(String clientAddress);
        void onDataTransferred(String clientAddress, long bytes);
    }

    private static class ClientSession {
        final String clientAddress;
        final long startTime;
        long bytesTransferred;

        ClientSession(String clientAddress) {
            this.clientAddress = clientAddress;
            this.startTime = System.currentTimeMillis();
            this.bytesTransferred = 0;
        }
    }

    public HttpProxyServer(Context context) {
        this.context = context;
        this.clientSessions = new ConcurrentHashMap<>();
    }

    public void setListener(ProxyListener listener) {
        this.listener = listener;
    }

    public void start() {
        if (isRunning) {
            return;
        }

        Log.i(TAG, "Starting HTTP proxy server on port " + PROXY_PORT);

        serverThread = new Thread(this::runServer);
        serverThread.start();
        isRunning = true;
    }

    public void stop() {
        if (!isRunning) {
            return;
        }

        Log.i(TAG, "Stopping HTTP proxy server");
        isRunning = false;

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing server socket", e);
        }

        if (serverThread != null) {
            serverThread.interrupt();
        }

        clientSessions.clear();
    }

    private void runServer() {
        try {
            serverSocket = new ServerSocket(PROXY_PORT, 50, InetAddress.getByName("0.0.0.0"));
            Log.i(TAG, "HTTP proxy server listening on port " + PROXY_PORT);

            while (isRunning && !serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    // Handle each client in a separate thread
                    new Thread(() -> handleClientConnection(clientSocket)).start();
                } catch (IOException e) {
                    if (isRunning) {
                        Log.e(TAG, "Error accepting client connection", e);
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error starting HTTP proxy server", e);
        }
    }

    private void handleClientConnection(Socket clientSocket) {
        String clientIp = clientSocket.getInetAddress().getHostAddress();
        DeviceConnectionManager deviceManager = DeviceConnectionManager.getInstance(context);
        ConnectedDevice device = deviceManager.getDeviceByIp(clientIp);

        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            OutputStream out = clientSocket.getOutputStream();

            String requestLine = in.readLine();
            if (requestLine == null) return;

            Log.d(TAG, "Received request from " + clientIp + ": " + requestLine);

            // Check if device is allowed
            if (device == null || !deviceManager.isDeviceAllowed(device.getDeviceId())) {
                sendErrorResponse(out, 403, "Forbidden - Device not authorized");
                return;
            }

            // Parse the request line
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                sendErrorResponse(out, 400, "Bad Request - Invalid request line");
                return;
            }

            String method = parts[0];
            String url = parts[1];

            // Read headers
            Map<String, String> headers = new HashMap<>();
            String headerLine;
            while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
                int colonIndex = headerLine.indexOf(':');
                if (colonIndex > 0) {
                    String headerName = headerLine.substring(0, colonIndex).trim();
                    String headerValue = headerLine.substring(colonIndex + 1).trim();
                    headers.put(headerName.toLowerCase(), headerValue);
                }
            }

            // Handle different HTTP methods
            if ("CONNECT".equalsIgnoreCase(method)) {
                // Handle HTTPS CONNECT request
                String targetHost = url.substring(0, url.indexOf(':'));
                int targetPort = Integer.parseInt(url.substring(url.indexOf(':') + 1));
                handleHttpsConnect(targetHost, targetPort, out, clientIp);
            } else {
                // Handle HTTP request
                handleHttpRequest(clientSocket, method, url, headers, device, clientIp);
            }

        } catch (IOException e) {
            Log.e(TAG, "Error handling client connection: " + clientIp, e);
            try {
                // Attempt to send an error response to the client
                sendErrorResponse(out, 500, "Internal Server Error");
            } catch (IOException ex) {
                Log.e(TAG, "Failed to send error response to client", ex);
            }
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing client socket", e);
            }

            clientSessions.remove(clientIp);
            if (listener != null) {
                listener.onClientDisconnected(clientIp);
            }
        }
    }


    private void handleHttpRequest(Socket clientSocket, String method, String url,
                                 Map<String, String> headers, ConnectedDevice device, String clientIp) {
        try {
            // Parse destination from URL or Host header
            String host = headers.get("host");
            if (host == null && url.startsWith("http")) {
                URL parsedUrl = new URL(url);
                host = parsedUrl.getHost();
            } else if (host == null) {
                // If Host header is missing and URL is not absolute, try to parse from request line
                if (url.startsWith("/")) {
                    String absoluteUrl = "http://" + headers.get("host") + url; // Assuming host is available
                    try {
                        URL parsedUrl = new URL(absoluteUrl);
                        host = parsedUrl.getHost();
                    } catch (Exception e) {
                        Log.e(TAG, "Could not parse URL from request line: " + url);
                    }
                }
            }


            if (host == null) {
                sendErrorResponse(clientSocket.getOutputStream(), 400, "Bad Request - No host specified");
                return;
            }

            int port = 80;
            if (host.contains(":")) {
                String[] hostParts = host.split(":");
                host = hostParts[0];
                port = Integer.parseInt(hostParts[1]);
            } else if (isStreamingDomain(host)) {
                port = 443; // Default to HTTPS for known streaming domains if not specified
            }


            Log.i(TAG, "Routing external device request to: " + host + ":" + port + " for client " + clientIp);

            // Create connection that routes through NetGuard VPN
            // This connection appears as the proxy app's UID in NetGuard's native code
            Socket targetSocket = new Socket();
            targetSocket.connect(new InetSocketAddress(host, port), 30000);

            // Forward the request
            PrintWriter targetWriter = new PrintWriter(targetSocket.getOutputStream(), true);
            targetWriter.println(method + " " + url + " HTTP/1.1");
            targetWriter.println("Host: " + host);

            // Forward other headers (except connection-specific ones)
            for (Map.Entry<String, String> header : headers.entrySet()) {
                if (!header.getKey().equals("host") &&
                    !header.getKey().equals("connection") &&
                    !header.getKey().equals("proxy-connection") &&
                    !header.getKey().equals("keep-alive") &&
                    !header.getKey().equals("transfer-encoding")) {
                    targetWriter.println(header.getKey() + ": " + header.getValue());
                }
            }
            targetWriter.println("Connection: close"); // Ensure connection is closed after response
            targetWriter.println();

            // Relay response back to external device with traffic monitoring
            relayResponse(targetSocket, clientSocket, device, clientIp);

            targetSocket.close();

        } catch (Exception e) {
            Log.e(TAG, "Error handling HTTP request for " + clientIp, e);
            try {
                sendErrorResponse(clientSocket.getOutputStream(), 500, "Internal Server Error");
            } catch (IOException ioException) {
                Log.e(TAG, "Error sending error response", ioException);
            }
        }
    }


    private void relayResponse(Socket sourceSocket, Socket clientSocket, ConnectedDevice device, String clientIp)
            throws IOException {
        byte[] buffer = new byte[4096];
        int bytesRead;
        long totalBytes = 0;

        try (InputStream sourceInput = sourceSocket.getInputStream();
             OutputStream clientOutput = clientSocket.getOutputStream()) {

            while ((bytesRead = sourceInput.read(buffer)) != -1) {
                clientOutput.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            clientOutput.flush(); // Ensure all buffered data is sent

            // Update device traffic statistics
            if (device != null) {
                DeviceConnectionManager.getInstance(context).updateDeviceTraffic(device.getDeviceId(), totalBytes);
                Log.d(TAG, "Traffic from " + device.getName() + " (" + clientIp + "): " + totalBytes + " bytes");
            } else {
                 Log.d(TAG, "Traffic from unknown device (" + clientIp + "): " + totalBytes + " bytes");
            }
        }
    }

    private void handleHttpsConnect(String targetHost, int targetPort, OutputStream out, String clientIp) {
        try {
            // Resolve DNS through NetGuard's VPN interface
            InetAddress targetAddress = InetAddress.getByName(targetHost);
            Socket targetSocket = new Socket(targetAddress, targetPort);

            out.write("HTTP/1.1 200 Connection established\r\n\r\n".getBytes());
            out.flush();

            Log.d(TAG, "HTTPS tunnel established to " + targetHost + ":" + targetPort + " for client " + clientIp);

            // Handle bidirectional HTTPS tunneling
            HttpsProxyHandler httpsHandler = new HttpsProxyHandler(context);
            httpsHandler.handleTunnel(targetSocket, out, clientIp);

        } catch (IOException e) {
            Log.e(TAG, "HTTPS CONNECT failed for " + targetHost + ":" + targetPort, e);
            try {
                String errorResponse = "HTTP/1.1 502 Bad Gateway\r\n" +
                                     "Content-Type: text/html\r\n" +
                                     "Content-Length: 50\r\n\r\n" +
                                     "<html><body><h1>502 Bad Gateway</h1></body></html>";
                out.write(errorResponse.getBytes());
                out.flush();
            } catch (IOException ex) {
                Log.e(TAG, "Failed to send error response", ex);
            }
        }
    }

    private boolean isStreamingDomain(String host) {
        String[] streamingDomains = {
            "youtube.com", "googlevideo.com", "ytimg.com",
            "netflix.com", "nflxvideo.net", "nflximg.net",
            "twitch.tv", "ttvnw.net",
            "facebook.com", "fbcdn.net",
            "instagram.com", "cdninstagram.com"
        };

        for (String domain : streamingDomains) {
            if (host.contains(domain)) {
                return true;
            }
        }
        return false;
    }

    private void sendErrorResponse(OutputStream out, int statusCode, String message) {
        try {
            String response = "HTTP/1.1 " + statusCode + " " + message + "\r\n" +
                            "Content-Type: text/html\r\n" +
                            "Content-Length: " + (message.length() + 50) + "\r\n\r\n" +
                            "<html><body><h1>" + statusCode + " " + message + "</h1></body></html>";
            out.write(response.getBytes());
            out.flush();
        } catch (IOException e) {
            Log.e(TAG, "Failed to send error response", e);
        }
    }


    public String getProxyUrl() {
        // This method might need to be updated to reflect the actual IP address of the device if it changes dynamically.
        // For now, using a common default.
        return "http://192.168.49.1:" + PROXY_PORT;
    }
}