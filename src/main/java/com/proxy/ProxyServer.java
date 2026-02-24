package com.proxy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyServer {

    private static final int PORT = 8080;
    private static final int THREAD_POOL_SIZE = 50;

    public static void main(String[] args) throws Exception {

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        ServerSocket serverSocket = new ServerSocket(PORT);
        startCommandListener(serverSocket, executor);

        System.out.println("Proxy Server running on port " + PORT);
        System.out.println("Type 'help' for CLI commands.");

        while (!serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                executor.submit(new ClientHandler(clientSocket));
            } catch (SocketException e) {
                if (serverSocket.isClosed()) {
                    break;
                }
                System.out.println("Accept error: " + e.getMessage());
            }
        }

        executor.shutdownNow();
    }

    private static void startCommandListener(ServerSocket serverSocket, ExecutorService executor) {
        Thread cliThread = new Thread(() -> {
            try (BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {
                String line;
                while (!serverSocket.isClosed() && (line = console.readLine()) != null) {
                    handleCommand(line, serverSocket, executor);
                }
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    System.out.println("CLI input error: " + e.getMessage());
                }
            }
        }, "proxy-cli");

        cliThread.setDaemon(true);
        cliThread.start();
    }

    private static void handleCommand(String line, ServerSocket serverSocket, ExecutorService executor) {
        String trimmed = line == null ? "" : line.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        String[] tokens = trimmed.split("\\s+");
        String command = tokens[0].toLowerCase();

        switch (command) {
            case "block":
                if (tokens.length < 2) {
                    System.out.println("Usage: block <host-or-url>");
                    return;
                }
                String targetToBlock = joinTokensFromIndex(tokens, 1);
                if (BlockedListManager.block(targetToBlock)) {
                    System.out.println("Blocked: " + targetToBlock);
                } else {
                    System.out.println("Target is already blocked or invalid: " + targetToBlock);
                }
                break;

            case "unblock":
                if (tokens.length < 2) {
                    System.out.println("Usage: unblock <host-or-url>");
                    return;
                }
                String targetToUnblock = joinTokensFromIndex(tokens, 1);
                if (BlockedListManager.unblock(targetToUnblock)) {
                    System.out.println("Unblocked: " + targetToUnblock);
                } else {
                    System.out.println("Target is not blocked or invalid: " + targetToUnblock);
                }
                break;

            case "blocklist":
                Set<String> hosts = BlockedListManager.getBlockedHosts();
                if (hosts.isEmpty()) {
                    System.out.println("Blocked list is empty.");
                } else {
                    System.out.println("Blocked targets (host/url normalized to host):");
                    hosts.forEach(host -> System.out.println("- " + host));
                }
                break;

            case "cache":
                handleCacheCommand(tokens);
                break;

            case "stats":
                System.out.println(ProxyStats.buildReport());
                break;

            case "help":
                printHelp();
                break;

            case "clear":
                clearConsole();
                break;

            case "quit":
            case "exit":
                System.out.println("Shutting down proxy server...");
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    System.out.println("Error closing server socket: " + e.getMessage());
                }
                executor.shutdownNow();
                break;

            default:
                System.out.println("Unknown command: " + command);
                printHelp();
        }
    }

    private static void printHelp() {
        System.out.println("Available commands:");
        System.out.println("  block <host-or-url>   - Add target to block list (normalized to host)");
        System.out.println("  unblock <host-or-url> - Remove target from block list (normalized to host)");
        System.out.println("  blocklist             - Show blocked targets");
        System.out.println("  cache list      - Show cached request keys");
        System.out.println("  cache clear     - Remove all cached responses");
        System.out.println("  cache remove <url> - Remove cached responses for a URL");
        System.out.println("  stats           - Show cache/network timing efficiency stats");
        System.out.println("  clear           - Clear console text");
        System.out.println("  help            - Show this message");
        System.out.println("  quit | exit     - Stop proxy server");
    }

    private static void clearConsole() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
        for (int i = 0; i < 100; i++) {
            System.out.println();
        }
    }

    private static void handleCacheCommand(String[] tokens) {
        if (tokens.length < 2) {
            System.out.println("Usage: cache <list|clear|remove>");
            return;
        }

        String subcommand = tokens[1].toLowerCase();
        switch (subcommand) {
            case "list":
                Set<String> cacheKeys = CacheManager.keys();
                if (cacheKeys.isEmpty()) {
                    System.out.println("Cache is empty.");
                } else {
                    System.out.println("Cache entries (" + cacheKeys.size() + "):");
                    cacheKeys.forEach(key -> System.out.println("- " + key));
                }
                break;

            case "clear":
                int beforeClear = CacheManager.size();
                CacheManager.clear();
                System.out.println("Cache cleared. Removed " + beforeClear + " entries.");
                break;

            case "remove":
                if (tokens.length < 3) {
                    System.out.println("Usage: cache remove <url>");
                    return;
                }
                String url = joinTokensFromIndex(tokens, 2);
                if (CacheManager.removeByUrl(url)) {
                    System.out.println("Removed cached entries for URL: " + url);
                } else {
                    System.out.println("No cached entries found for URL: " + url);
                }
                break;

            default:
                System.out.println("Unknown cache command: " + subcommand);
                System.out.println("Usage: cache <list|clear|remove>");
        }
    }

    private static String joinTokensFromIndex(String[] tokens, int startIndex) {
        StringBuilder builder = new StringBuilder();
        for (int i = startIndex; i < tokens.length; i++) {
            if (i > startIndex) {
                builder.append(' ');
            }
            builder.append(tokens[i]);
        }
        return builder.toString();
    }
}
