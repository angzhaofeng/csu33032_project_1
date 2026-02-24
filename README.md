# csu33032_project_1
Simple Web Proxy Project for CSU33032

# Project Summary
## Overview
This project implements a simple multithreaded HTTP web proxy server in Java. The proxy listens
on a specified port (default 8080) and forwards client requests to origin servers, relaying responses back to clients. Key features include:
- **HTTP Request Handling**: Supports basic HTTP/1.1 requests and responses
- **Multi-threading**: Uses a thread pool to handle multiple client connections concurrently
- **Caching**: Implements an LRU cache to store HTTP responses and improve performance
- **URL Blocking**: Allows dynamic blocking of specific domains via a management console
- **Management Console**: Provides an interactive CLI for monitoring and controlling the proxy

## Instructions
- **Build**: Use Maven to compile and package the application in root folder
    ```bash
    mvn clean package
    ```
- **Run**: Execute the JAR file to start the proxy server
    ```bash
    run.bat
    ```
- **Default Port**: 8080
- **Browser Config**: Set HTTP proxy to 127.0.0.1:8080
- **Management Console**: Type commands in the server console (e.g., `block example.com`)

## Console Commands
```bash
block <host-or-url>   - Add target to block list (normalized to host)
unblock <host-or-url> - Remove target from block list (normalized to host)
blocklist             - Show blocked targets
cache list      - Show cached request keys
cache clear     - Remove all cached responses
cache remove <url> - Remove cached responses for a URL
stats           - Show cache/network timing efficiency stats
clear           - Clear console text
help            - Show this message
quit | exit     - Stop proxy server
```

## Architecture
The proxy server is structured into several components:
```
ProxyServer (Main)
├── ClientHandler - Handles individual client requests
├── CacheManager - Manages the LRU cache for HTTP responses
├── BlockListManager - Manages the list of blocked domains
└── ManagementConsole - Provides an interactive command-line interface (To Be Implemented)
``` 

## Features Implemented
### ✅ HTTP Request Handling
- Parses HTTP requests and forwards them to origin servers
- Relays responses back to clients with proper headers
### ✅ Multi-threading
- Uses a fixed thread pool (50 threads) to handle multiple clients simultaneously
### ✅ Caching
- Implements an LRU cache with a capacity of 1000 items
- Caches HTTP GET responses with a 200 status code
- Tracks cache hit rate for performance analysis
### ✅ URL Blocking
- Allows blocking of specific domains via CLI commands
- Supports wildcard patterns for blocking (e.g., `*.ads.com`)
### ✅ Management Console (To Be Implemented)
- Provides commands for blocking/unblocking domains, viewing cache stats, and monitoring requests

## Testing
### Unit Testing ✅
- Used JUnit for testing CacheManager and BlockListManager
- CacheManager tests: cache insertion, eviction, hit/miss tracking
- BlockListManager tests: blocking/unblocking domains, wildcard matching
### Integration Testing ✅
- Simulated client requests using Java sockets
- Verified correct request forwarding, response relaying, caching behavior, and blocking functionality
### Browser Testing ✅
- Configured Firefox to use the proxy at 127.0.0.1:8080
- Tested loading of HTTP and HTTPS sites, confirming proper caching and blocking behavior
## Performance
- HTTP requests are served at full speed when cached
- HTTPS requests are relayed with minimal overhead due to tunneling
- Memory usage is optimized with LRU cache eviction