package com.proxy;

import java.io.*;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private static final int BUFFER_SIZE = 8192;
    private static final int CONNECT_TIMEOUT_MS = 10000;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        try (
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream()));
                OutputStream clientOut = clientSocket.getOutputStream()
        ) {

            String requestLine;
            try {
                requestLine = reader.readLine();
            } catch (SocketException e) {
                return;
            }
            if (requestLine == null) return;

            System.out.println("Request: " + requestLine);

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                sendBadRequest(clientOut);
                return;
            }
            String method = parts[0];
            String urlString = parts[1];

            long requestStartTime = System.nanoTime();

            try {
                if ("CONNECT".equalsIgnoreCase(method)) {
                    handleHttpsTunnel(urlString, clientOut);
                } else {
                    handleHttpRequest(method, urlString, requestLine, reader, clientOut);
                }
            } finally {
                long requestDurationMs = (System.nanoTime() - requestStartTime) / 1_000_000;
                System.out.println("Request Completed: " + method + " " + urlString +
                        " | Time: " + requestDurationMs + " ms");
            }

        } catch (SocketException e) {
            System.out.println("Client disconnected: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("I/O error while handling client: " + e.getMessage());
        }
    }

    private void handleHttpRequest(String method, String urlString,
                               String requestLine,
                               BufferedReader reader,
                               OutputStream clientOut) throws IOException {

    long startTime = System.nanoTime();   // ⏱ start timer

    URL url = new URL(urlString);
    String host = url.getHost();
    int port = (url.getPort() == -1) ? 80 : url.getPort();

    if (checkBlockedList(host, urlString, clientOut)) {
        return;
    }

    String cacheKey = method + ":" + urlString;

    // ================= CACHE HIT =================
    if ("GET".equalsIgnoreCase(method) && CacheManager.contains(cacheKey)) {

        byte[] cachedResponse = CacheManager.get(cacheKey);
        clientOut.write(cachedResponse);
        clientOut.flush();

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;

        ProxyStats.recordCacheHit(durationMs);
        System.out.println("Cache Retrieved: " + host +
                " | Time: " + durationMs + " ms");

        return;
    }

    // ================= NETWORK REQUEST =================
    try (Socket serverSocket = new Socket()) {

        serverSocket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);

        OutputStream serverOut = serverSocket.getOutputStream();
        InputStream serverIn = serverSocket.getInputStream();

        serverOut.write((requestLine + "\r\n").getBytes());

        boolean connectionHeaderSeen = false;
        boolean proxyConnectionHeaderSeen = false;

        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            String lowerLine = line.toLowerCase();

            if (lowerLine.startsWith("connection:")) {
                serverOut.write("Connection: close\r\n".getBytes());
                connectionHeaderSeen = true;
                continue;
            }

            if (lowerLine.startsWith("proxy-connection:")) {
                serverOut.write("Proxy-Connection: close\r\n".getBytes());
                proxyConnectionHeaderSeen = true;
                continue;
            }

            serverOut.write((line + "\r\n").getBytes());
        }

        if (!connectionHeaderSeen) {
            serverOut.write("Connection: close\r\n".getBytes());
        }

        if (!proxyConnectionHeaderSeen) {
            serverOut.write("Proxy-Connection: close\r\n".getBytes());
        }

        serverOut.write("\r\n".getBytes());
        serverOut.flush();

        ByteArrayOutputStream responseBuffer = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;

        while ((bytesRead = serverIn.read(buffer)) != -1) {
            responseBuffer.write(buffer, 0, bytesRead);
            clientOut.write(buffer, 0, bytesRead);
        }

        clientOut.flush();

        if ("GET".equalsIgnoreCase(method)) {
            CacheManager.put(cacheKey, responseBuffer.toByteArray());
        }

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;

        ProxyStats.recordNetworkFetch(durationMs);
        System.out.println("Fetched From Network: " + host +
                " | Time: " + durationMs + " ms");

    } catch (SocketTimeoutException | ConnectException e) {
        System.out.println("Upstream connection timeout for " + host + ":" + port);
        sendGatewayTimeout(clientOut);
    } catch (IOException e) {
        System.out.println("Upstream I/O error for " + host + ":" + port + " - " + e.getMessage());
        sendBadGateway(clientOut);
    }
}

    private void handleHttpsTunnel(String hostPort, OutputStream clientOut) throws IOException {

        long tunnelStartTime = System.nanoTime();

        String[] parts = hostPort.split(":");
        if (parts.length != 2) {
            sendBadRequest(clientOut);
            return;
        }
        String host = parts[0];
        int port;
        try {
            port = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            sendBadRequest(clientOut);
            return;
        }

        if (checkBlockedList(host, null, clientOut)) {
            try {
                clientSocket.shutdownOutput();
            } catch (IOException ignored) {
            }
            return;
        }

        Socket serverSocket = new Socket();
        try {
            serverSocket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        } catch (SocketTimeoutException | ConnectException e) {
            long durationMs = (System.nanoTime() - tunnelStartTime) / 1_000_000;
            System.out.println("HTTPS tunnel timeout for " + host + ":" + port);
            System.out.println("HTTPS Tunnel Failed: " + host + ":" + port +
                    " | Time: " + durationMs + " ms");
            sendGatewayTimeout(clientOut);
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            return;
        } catch (IOException e) {
            long durationMs = (System.nanoTime() - tunnelStartTime) / 1_000_000;
            System.out.println("HTTPS tunnel failed for " + host + ":" + port + " - " + e.getMessage());
            System.out.println("HTTPS Tunnel Failed: " + host + ":" + port +
                    " | Time: " + durationMs + " ms");
            sendBadGateway(clientOut);
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            return;
        }

        clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes());
        clientOut.flush();

    long establishedDurationMs = (System.nanoTime() - tunnelStartTime) / 1_000_000;
    System.out.println("HTTPS Tunnel Established: " + host + ":" + port +
        " | Time: " + establishedDurationMs + " ms");

        Thread t1 = new Thread(() -> pipe(clientSocket, serverSocket));
        Thread t2 = new Thread(() -> pipe(serverSocket, clientSocket));

        t1.start();
        t2.start();
        
        try {
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private boolean checkBlockedList(String host, String urlString, OutputStream clientOut) throws IOException {
        String target = (urlString != null && !urlString.isBlank()) ? urlString : host;
        if (BlockedListManager.isBlocked(target)) {
            System.out.println("Blocked: " + target);
            ProxyStats.recordBlockedRequest();
            sendForbidden(clientOut);
            return true;
        }
        return false;
    }

    private void pipe(Socket inputSocket, Socket outputSocket) {
        try {
            InputStream in = inputSocket.getInputStream();
            OutputStream out = outputSocket.getOutputStream();
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                out.flush();
            }
        } catch (IOException ignored) {
        }
    }

    private void sendForbidden(OutputStream out) throws IOException {
        String body = "Blocked by Proxy";
        byte[] bodyBytes = body.getBytes();
        String response = "HTTP/1.1 403 Forbidden\r\n"
                + "Content-Type: text/plain; charset=utf-8\r\n"
                + "Content-Length: " + bodyBytes.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n";
        out.write(response.getBytes());
        out.write(bodyBytes);
        out.flush();
    }

    private void sendBadRequest(OutputStream out) throws IOException {
        String body = "Invalid proxy request";
        byte[] bodyBytes = body.getBytes();
        String response = "HTTP/1.1 400 Bad Request\r\n"
                + "Content-Type: text/plain; charset=utf-8\r\n"
                + "Content-Length: " + bodyBytes.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n";
        out.write(response.getBytes());
        out.write(bodyBytes);
        out.flush();
    }

    private void sendGatewayTimeout(OutputStream out) throws IOException {
        String body = "Upstream connection timed out";
        byte[] bodyBytes = body.getBytes();
        String response = "HTTP/1.1 504 Gateway Timeout\r\n"
                + "Content-Type: text/plain; charset=utf-8\r\n"
                + "Content-Length: " + bodyBytes.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n";
        out.write(response.getBytes());
        out.write(bodyBytes);
        out.flush();
    }

    private void sendBadGateway(OutputStream out) throws IOException {
        String body = "Upstream connection failed";
        byte[] bodyBytes = body.getBytes();
        String response = "HTTP/1.1 502 Bad Gateway\r\n"
                + "Content-Type: text/plain; charset=utf-8\r\n"
                + "Content-Length: " + bodyBytes.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n";
        out.write(response.getBytes());
        out.write(bodyBytes);
        out.flush();
    }
}
