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

            if ("CONNECT".equalsIgnoreCase(method)) {
                handleHttpsTunnel(urlString, clientOut);
            } else {
                handleHttpRequest(method, urlString, requestLine, reader, clientOut);
            }

        } catch (SocketException e) {
            System.out.println("Client disconnected: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("I/O error while handling client: " + e.getMessage());
        }
    }

    // private void handleHttpRequest(String method, String urlString,
    //                                String requestLine,
    //                                BufferedReader reader,
    //                                OutputStream clientOut) throws IOException {

    //     URL url = new URL(urlString);
    //     String host = url.getHost();
    //     int port = (url.getPort() == -1) ? 80 : url.getPort();

    //     if (checkBlockedList(host, clientOut)) {
    //         return;
    //     }


    //     // if (BlockedListManager.isBlocked(host)) {
    //     //     System.out.println("Blocked: " + host);
    //     //     sendForbidden(clientOut);
    //     //     return;
    //     // }

    //     String cacheKey = method + ":" + urlString;

    //     if ("GET".equalsIgnoreCase(method) && CacheManager.contains(cacheKey)) {
    //         clientOut.write(CacheManager.get(cacheKey));
    //         System.out.println("Cache Retrieved: " + host);
    //         clientOut.flush();
    //         return;
    //     }

    //     try (Socket serverSocket = new Socket()) {
    //         serverSocket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);

    //         OutputStream serverOut = serverSocket.getOutputStream();
    //         InputStream serverIn = serverSocket.getInputStream();

    //         // Send original request
    //         serverOut.write((requestLine + "\r\n").getBytes());

    //         String line;
    //         while ((line = reader.readLine()) != null && !line.isEmpty()) {
    //             serverOut.write((line + "\r\n").getBytes());
    //         }
    //         serverOut.write("\r\n".getBytes());
    //         serverOut.flush();

    //         ByteArrayOutputStream responseBuffer = new ByteArrayOutputStream();
    //         byte[] buffer = new byte[BUFFER_SIZE];
    //         int bytesRead;

    //         while ((bytesRead = serverIn.read(buffer)) != -1) {
    //             responseBuffer.write(buffer, 0, bytesRead);
    //             clientOut.write(buffer, 0, bytesRead);
    //         }

    //         clientOut.flush();

    //         if ("GET".equalsIgnoreCase(method)) {
    //             CacheManager.put(cacheKey, responseBuffer.toByteArray());
    //         }
    //     } catch (SocketTimeoutException | ConnectException e) {
    //         System.out.println("Upstream connection timeout for " + host + ":" + port);
    //         sendGatewayTimeout(clientOut);
    //     } catch (IOException e) {
    //         System.out.println("Upstream I/O error for " + host + ":" + port + " - " + e.getMessage());
    //         sendBadGateway(clientOut);
    //     }
    // }

    private void handleHttpRequest(String method, String urlString,
                               String requestLine,
                               BufferedReader reader,
                               OutputStream clientOut) throws IOException {

    long startTime = System.nanoTime();   // ⏱ start timer

    URL url = new URL(urlString);
    String host = url.getHost();
    int port = (url.getPort() == -1) ? 80 : url.getPort();

    if (checkBlockedList(host, clientOut)) {
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

        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            serverOut.write((line + "\r\n").getBytes());
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

        if (checkBlockedList(host, clientOut)) {
            try {
                clientSocket.shutdownOutput();
            } catch (IOException ignored) {
            }
            return;
        }

        // if (BlockedListManager.isBlocked(host)) {
        //     System.out.println("Blocked: " + host);
        //     sendForbidden(clientOut);
        //     return;
        // }

        Socket serverSocket = new Socket();
        try {
            serverSocket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        } catch (SocketTimeoutException | ConnectException e) {
            System.out.println("HTTPS tunnel timeout for " + host + ":" + port);
            sendGatewayTimeout(clientOut);
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            return;
        } catch (IOException e) {
            System.out.println("HTTPS tunnel failed for " + host + ":" + port + " - " + e.getMessage());
            sendBadGateway(clientOut);
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            return;
        }

        clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes());
        clientOut.flush();

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

    private boolean checkBlockedList(String host, OutputStream clientOut) throws IOException {
        if (BlockedListManager.isBlocked(host)) {
            System.out.println("Blocked: " + host);
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
        String response = "HTTP/1.1 403 Forbidden\r\n\r\nBlocked by Proxy";
        out.write(response.getBytes());
        out.flush();
    }

    private void sendBadRequest(OutputStream out) throws IOException {
        String response = "HTTP/1.1 400 Bad Request\r\n\r\nInvalid proxy request";
        out.write(response.getBytes());
        out.flush();
    }

    private void sendGatewayTimeout(OutputStream out) throws IOException {
        String response = "HTTP/1.1 504 Gateway Timeout\r\n\r\nUpstream connection timed out";
        out.write(response.getBytes());
        out.flush();
    }

    private void sendBadGateway(OutputStream out) throws IOException {
        String response = "HTTP/1.1 502 Bad Gateway\r\n\r\nUpstream connection failed";
        out.write(response.getBytes());
        out.flush();
    }
}
