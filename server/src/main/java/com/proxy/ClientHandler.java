package com.proxy;

import java.io.*;
import java.net.Socket;
import java.net.URL;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private static final int BUFFER_SIZE = 8192;

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

            String requestLine = reader.readLine();
            if (requestLine == null) return;

            System.out.println("Request: " + requestLine);

            String[] parts = requestLine.split(" ");
            String method = parts[0];
            String urlString = parts[1];

            if ("CONNECT".equalsIgnoreCase(method)) {
                handleHttpsTunnel(urlString, clientOut);
            } else {
                handleHttpRequest(method, urlString, requestLine, reader, clientOut);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleHttpRequest(String method, String urlString,
                                   String requestLine,
                                   BufferedReader reader,
                                   OutputStream clientOut) throws Exception {

        URL url = new URL(urlString);
        String host = url.getHost();
        int port = (url.getPort() == -1) ? 80 : url.getPort();

        if (checkBlockedList(host, clientOut)) {
            return;
        }


        // if (BlockedListManager.isBlocked(host)) {
        //     System.out.println("Blocked: " + host);
        //     sendForbidden(clientOut);
        //     return;
        // }

        String cacheKey = method + ":" + urlString;

        if ("GET".equalsIgnoreCase(method) && CacheManager.contains(cacheKey)) {
            clientOut.write(CacheManager.get(cacheKey));
            clientOut.flush();
            return;
        }

        try (Socket serverSocket = new Socket(host, port)) {

            OutputStream serverOut = serverSocket.getOutputStream();
            InputStream serverIn = serverSocket.getInputStream();

            // Send original request
            serverOut.write((requestLine + "\r\n").getBytes());

            String line;
            while (!(line = reader.readLine()).isEmpty()) {
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
        }
    }

    private void handleHttpsTunnel(String hostPort, OutputStream clientOut) throws Exception {

        String[] parts = hostPort.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);

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

        Socket serverSocket = new Socket(host, port);

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
}
