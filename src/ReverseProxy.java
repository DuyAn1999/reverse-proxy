import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.List;
import java.util.Map;
import com.teamdev.jxbrowser.chromium.Browser;
import com.teamdev.jxbrowser.chromium.ProxyConfig;
import com.teamdev.jxbrowser.chromium.swing.BrowserView;
import com.teamdev.jxbrowser.chromium.BrowserType;
import com.teamdev.jxbrowser.chromium.swing.BrowserView;
import java.awt.BorderLayout;

import javax.swing.*;

public class ReverseProxy {
    public static void main(String[] args) throws IOException {
        // Create HTTP server on localhost:8000
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8000), 0);
        server.createContext("/", new ProxyHandler());
        server.setExecutor(null); // Use default executor
        server.start();
        System.out.println("Reverse Proxy running on http://localhost:8000");

        JFrame frame = new JFrame();
        frame.setBounds(100, 100, 1000, 1000);
        frame.setTitle("Test");

        Browser browser = new Browser(BrowserType.HEAVYWEIGHT);
        BrowserView view = new BrowserView(browser);
        frame.add(view, BorderLayout.CENTER);
        frame.setVisible(true);

        // browser.loadURL("http://localhost:8000");
    }

    static class ProxyHandler implements HttpHandler {
        // Target server URL (replace with your target site)
        private static final String TARGET_URL = "https://devonline01.orangelogic.com";

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                // Get request details
                String requestMethod = exchange.getRequestMethod();
                String path = exchange.getRequestURI().getPath();

                System.out.println("Handling request " + requestMethod + " " + path );
                URL targetUrl = new URL(TARGET_URL + path);

                // Open connection to target server
                HttpURLConnection conn = (HttpURLConnection) targetUrl.openConnection();
                conn.setInstanceFollowRedirects(true); // Enable redirect following
                conn.setRequestMethod(requestMethod);

                // Copy specific request headers, excluding Accept-Encoding
                Map<String, List<String>> reqHeaders = exchange.getRequestHeaders();
//                String[] headersToCopy = {
//                        "Accept", "Connection", "User-Agent", "Accept-Language", "Upgrade-Insecure-Requests"
//                };
//                for (String header : headersToCopy) {
//                    if (reqHeaders.containsKey(header)) {
//                        for (String value : reqHeaders.get(header)) {
//                            conn.addRequestProperty(header, value);
//                        }
//                    }
//                }
                for (Map.Entry<String, List<String>>  header : reqHeaders.entrySet()) {
                    for (String value : header.getValue()) {
                        if (!header.getKey().equals("Accept-encoding"))
                        {
                            conn.addRequestProperty(header.getKey(), value);
                            System.out.println("Copy header request " + header.getKey() + " " + value );
                        }
                    }
                }
                conn.setRequestProperty("Host", targetUrl.getHost());
                conn.setRequestProperty("Accept-Encoding", "identity");

                // Handle request body for POST/PUT
                if ("POST".equalsIgnoreCase(requestMethod) || "PUT".equalsIgnoreCase(requestMethod)) {
                    conn.setDoOutput(true);
                    try (InputStream is = exchange.getRequestBody();
                         OutputStream os = conn.getOutputStream()) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                    }
                }

                // Get response from target server
                int responseCode = conn.getResponseCode();
                exchange.sendResponseHeaders(responseCode, 0);

                // Determine Content-Type based on path
                String contentType = conn.getHeaderField("Content-Type");
                if (path.endsWith(".js")) {
                    contentType = "application/javascript; charset=UTF-8";
                } else if (contentType == null || !contentType.contains("text/html")) {
                    contentType = "text/html; charset=UTF-8";
                }

                exchange.getResponseHeaders().set("Content-Type", contentType);

                // Copy response headers
                Map<String, List<String>> respHeaders = conn.getHeaderFields();
                for (Map.Entry<String, List<String>> header : respHeaders.entrySet()) {
                    String key = header.getKey();
                    if (key != null && !key.equalsIgnoreCase("Transfer-Encoding") &&
                            !key.equalsIgnoreCase("Content-Length") && !key.equalsIgnoreCase("Content-Encoding")) {
                        for (String value : header.getValue()) {
                            exchange.getResponseHeaders().add(key, value);
                            System.out.println("Copy header response " + header.getKey() + " " + value );
                        }
                    }
                }

                // Prevent Content-Disposition
                exchange.getResponseHeaders().remove("Content-Disposition");

                // Add CORS header for JavaScript files
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");

                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "DNT,User-Agent,X-Requested-With,If-Modified-Since,Cache-Control,Content-Type,Range");

                // Copy response body
                try (InputStream is = responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
                     OutputStream os = exchange.getResponseBody()) {
                    if (is != null) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                exchange.sendResponseHeaders(500, -1);
            } finally {
                exchange.close();
            }
        }
    }
}