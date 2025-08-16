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
        String clientAddress = clientSocket.getRemoteSocketAddress().toString();
        ClientSession session = new ClientSession(clientAddress);
        clientSessions.put(clientAddress, session);

        Log.d(TAG, "External device connected: " + clientAddress);

        if (listener != null) {
            listener.onClientConnected(clientAddress);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()))) {

            String requestLine = reader.readLine();
            if (requestLine != null) {
                Log.d(TAG, "HTTP Request from external device: " + requestLine);

                // Parse HTTP request
                String[] parts = requestLine.split(" ");
                if (parts.length >= 3) {
                    String method = parts[0];
                    String url = parts[1];
                    String version = parts[2];

                    // Read headers
                    Map<String, String> headers = new HashMap<>();
                    String headerLine;
                    while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
                        int colonIndex = headerLine.indexOf(':');
                        if (colonIndex > 0) {
                            String headerName = headerLine.substring(0, colonIndex).trim();
                            String headerValue = headerLine.substring(colonIndex + 1).trim();
                            headers.put(headerName.toLowerCase(), headerValue);
                        }
                    }

                    // Route through NetGuard VPN
                    handleHttpRequest(clientSocket, method, url, headers, session);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error handling client connection: " + clientAddress, e);
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing client socket", e);
            }

            clientSessions.remove(clientAddress);
            if (listener != null) {
                listener.onClientDisconnected(clientAddress);
            }
        }
    }

    private void handleHttpRequest(Socket clientSocket, String method, String url,
                                 Map<String, String> headers, ClientSession session) {
        try {
            // Parse destination from URL or Host header
            String host = headers.get("host");
            if (host == null && url.startsWith("http")) {
                URL parsedUrl = new URL(url);
                host = parsedUrl.getHost();
            }

            if (host == null) {
                sendHttpError(clientSocket, 400, "Bad Request - No host specified");
                return;
            }

            int port = 80;
            if (host.contains(":")) {
                String[] hostParts = host.split(":");
                host = hostParts[0];
                port = Integer.parseInt(hostParts[1]);
            }

            Log.i(TAG, "Routing external device request to: " + host + ":" + port);

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
                    !header.getKey().equals("proxy-connection")) {
                    targetWriter.println(header.getKey() + ": " + header.getValue());
                }
            }
            targetWriter.println("Connection: close");
            targetWriter.println();

            // Relay response back to external device
            relayResponse(targetSocket, clientSocket, session);

            targetSocket.close();

        } catch (Exception e) {
            Log.e(TAG, "Error handling HTTP request", e);
            try {
                sendHttpError(clientSocket, 500, "Internal Server Error");
            } catch (IOException ioException) {
                Log.e(TAG, "Error sending error response", ioException);
            }
        }
    }

    private void relayResponse(Socket sourceSocket, Socket clientSocket, ClientSession session)
            throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;

        try (InputStream sourceInput = sourceSocket.getInputStream();
             OutputStream clientOutput = clientSocket.getOutputStream()) {

            while ((bytesRead = sourceInput.read(buffer)) != -1) {
                clientOutput.write(buffer, 0, bytesRead);

                // Track data usage for external device
                session.bytesTransferred += bytesRead;
                if (listener != null) {
                    listener.onDataTransferred(session.clientAddress, bytesRead);
                }
            }
        }
    }

    private void sendHttpError(Socket clientSocket, int statusCode, String message)
            throws IOException {
        PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
        writer.println("HTTP/1.1 " + statusCode + " " + message);
        writer.println("Content-Type: text/html");
        writer.println("Connection: close");
        writer.println();
        writer.println("<html><body><h1>" + statusCode + " " + message + "</h1></body></html>");
    }

    public String getProxyUrl() {
        return "http://192.168.49.1:" + PROXY_PORT;
    }
}