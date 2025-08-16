package eu.faircode.netguard;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HttpProxyServer {
    private static final String TAG = "NetGuard.ProxyServer";
    private static final int PROXY_PORT = 8888;
    private static final String PROXY_HOST = "192.168.49.1";

    private ServerSocket serverSocket;
    private ExecutorService executor;
    private boolean isRunning = false;
    private ProxyListener listener;

    public interface ProxyListener {
        void onClientConnected(String clientAddress);
        void onClientDisconnected(String clientAddress);
        void onDataTransferred(String clientAddress, long bytes);
    }

    public HttpProxyServer(ProxyListener listener) {
        this.listener = listener;
        this.executor = Executors.newCachedThreadPool();
    }

    public void start() {
        if (isRunning) {
            Log.w(TAG, "Proxy server already running");
            return;
        }

        executor.execute(() -> {
            try {
                serverSocket = new ServerSocket(PROXY_PORT);
                isRunning = true;
                Log.i(TAG, "Proxy server started on " + PROXY_HOST + ":" + PROXY_PORT);

                while (isRunning && !serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();
                    executor.execute(new ProxyClientHandler(clientSocket));
                }
            } catch (IOException e) {
                if (isRunning) {
                    Log.e(TAG, "Proxy server error: " + e.getMessage());
                }
            }
        });
    }

    public void stop() {
        if (!isRunning) {
            return;
        }

        isRunning = false;

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error stopping proxy server: " + e.getMessage());
        }

        executor.shutdown();
        Log.i(TAG, "Proxy server stopped");
    }

    public boolean isRunning() {
        return isRunning;
    }

    public String getProxyUrl() {
        return "http://" + PROXY_HOST + ":" + PROXY_PORT;
    }

    private class ProxyClientHandler implements Runnable {
        private Socket clientSocket;
        private String clientAddress;

        public ProxyClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
            this.clientAddress = clientSocket.getRemoteSocketAddress().toString();
        }

        @Override
        public void run() {
            try {
                Log.d(TAG, "Client connected: " + clientAddress);
                if (listener != null) {
                    listener.onClientConnected(clientAddress);
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                OutputStream clientOutput = clientSocket.getOutputStream();

                String requestLine = reader.readLine();
                if (requestLine == null) {
                    return;
                }

                Log.d(TAG, "Request: " + requestLine);

                // Parse HTTP request
                String[] parts = requestLine.split(" ");
                if (parts.length < 3) {
                    sendErrorResponse(clientOutput, 400, "Bad Request");
                    return;
                }

                String method = parts[0];
                String url = parts[1];
                String httpVersion = parts[2];

                // Handle CONNECT method for HTTPS
                if ("CONNECT".equals(method)) {
                    handleConnect(url, clientOutput, reader);
                } else {
                    // Handle HTTP requests
                    handleHttpRequest(method, url, httpVersion, reader, clientOutput);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error handling client: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                    if (listener != null) {
                        listener.onClientDisconnected(clientAddress);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error closing client socket: " + e.getMessage());
                }
            }
        }

        private void handleConnect(String target, OutputStream clientOutput, BufferedReader reader) {
            try {
                String[] hostPort = target.split(":");
                String host = hostPort[0];
                int port = hostPort.length > 1 ? Integer.parseInt(hostPort[1]) : 443;

                // Create connection to target server
                Socket targetSocket = new Socket(host, port);

                // Send 200 Connection Established
                clientOutput.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes());
                clientOutput.flush();

                // Start tunneling
                ExecutorService tunnelExecutor = Executors.newFixedThreadPool(2);

                tunnelExecutor.execute(() -> {
                    try {
                        byte[] buffer = new byte[4096];
                        InputStream clientInput = clientSocket.getInputStream();
                        OutputStream targetOutput = targetSocket.getOutputStream();

                        int bytesRead;
                        while ((bytesRead = clientInput.read(buffer)) != -1) {
                            targetOutput.write(buffer, 0, bytesRead);
                            targetOutput.flush();
                            if (listener != null) {
                                listener.onDataTransferred(clientAddress, bytesRead);
                            }
                        }
                    } catch (IOException e) {
                        Log.d(TAG, "Client to target tunnel closed");
                    }
                });

                tunnelExecutor.execute(() -> {
                    try {
                        byte[] buffer = new byte[4096];
                        InputStream targetInput = targetSocket.getInputStream();

                        int bytesRead;
                        while ((bytesRead = targetInput.read(buffer)) != -1) {
                            clientOutput.write(buffer, 0, bytesRead);
                            clientOutput.flush();
                            if (listener != null) {
                                listener.onDataTransferred(clientAddress, bytesRead);
                            }
                        }
                    } catch (IOException e) {
                        Log.d(TAG, "Target to client tunnel closed");
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error in CONNECT tunnel: " + e.getMessage());
                try {
                    sendErrorResponse(clientOutput, 502, "Bad Gateway");
                } catch (IOException ex) {
                    Log.e(TAG, "Error sending error response: " + ex.getMessage());
                }
            }
        }

        private void handleHttpRequest(String method, String url, String httpVersion,
                                     BufferedReader reader, OutputStream clientOutput) throws IOException {
            try {
                // Make request to target server
                URL targetUrl = new URL(url);
                HttpURLConnection connection = (HttpURLConnection) targetUrl.openConnection();
                connection.setRequestMethod(method);

                // Copy headers from client request
                String header;
                while ((header = reader.readLine()) != null && !header.isEmpty()) {
                    String[] headerParts = header.split(": ", 2);
                    if (headerParts.length == 2) {
                        connection.setRequestProperty(headerParts[0], headerParts[1]);
                    }
                }

                // Get response
                int responseCode = connection.getResponseCode();
                String responseMessage = connection.getResponseMessage();

                // Send response status
                String statusLine = httpVersion + " " + responseCode + " " + responseMessage + "\r\n";
                clientOutput.write(statusLine.getBytes());

                // Send response headers
                for (String key : connection.getHeaderFields().keySet()) {
                    if (key != null) {
                        String value = connection.getHeaderField(key);
                        String headerLine = key + ": " + value + "\r\n";
                        clientOutput.write(headerLine.getBytes());
                    }
                }
                clientOutput.write("\r\n".getBytes());

                // Send response body
                InputStream responseStream = connection.getInputStream();
                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalBytes = 0;
                while ((bytesRead = responseStream.read(buffer)) != -1) {
                    clientOutput.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                }

                if (listener != null) {
                    listener.onDataTransferred(clientAddress, totalBytes);
                }

                responseStream.close();
                connection.disconnect();

            } catch (Exception e) {
                Log.e(TAG, "Error handling HTTP request: " + e.getMessage());
                sendErrorResponse(clientOutput, 502, "Bad Gateway");
            }
        }

        private void sendErrorResponse(OutputStream output, int code, String message) throws IOException {
            String response = "HTTP/1.1 " + code + " " + message + "\r\n" +
                            "Content-Type: text/html\r\n" +
                            "Content-Length: 0\r\n" +
                            "\r\n";
            output.write(response.getBytes());
            output.flush();
        }
    }
}