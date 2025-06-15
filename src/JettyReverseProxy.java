import com.teamdev.jxbrowser.chromium.Browser;
import com.teamdev.jxbrowser.chromium.BrowserType;
import com.teamdev.jxbrowser.chromium.swing.BrowserView;
import com.teamdev.jxbrowser.chromium.events.ConsoleEvent;
import com.teamdev.jxbrowser.chromium.events.ConsoleListener;
import org.eclipse.jetty.proxy.ProxyServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import java.awt.BorderLayout;

public class JettyReverseProxy {
    public static void main(String[] args) throws Exception {


        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    Server server = new Server(8000);
                    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
                    context.setContextPath("/");

                    ServletHolder proxyHolder = new ServletHolder(new CustomProxyServlet());
                    context.addServlet(proxyHolder, "/*");

                    server.setHandler(context);
                    server.start();
                    System.out.println("Jetty Reverse Proxy running on http://localhost:8000");
                    server.join();
                }
                catch (Exception e){
                    e.printStackTrace();
                }
            }
        });
        Thread.sleep(1000);

        JFrame frame = new JFrame();
        frame.setBounds(100, 100, 1000, 1000);
        frame.setTitle("Test");

        Browser browser = new Browser(BrowserType.HEAVYWEIGHT);
        BrowserView view = new BrowserView(browser);
        frame.add(view, BorderLayout.CENTER);
        frame.setVisible(true);
        browser.addConsoleListener(new ConsoleListener()
        {
            public void onMessage(ConsoleEvent event)
            {
                System.out.println("Level: " + event.getLevel() + ".Message: " + event.getMessage() + ". Line: " + event.getLineNumber() + " .Source: " + event.getSource());
            }
        });
        browser.loadURL("http://localhost:8000");
    }

    static class CustomProxyServlet extends ProxyServlet {
        private static final String TARGET_URL = "https://google.com"; // Replace with your site

        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String path = request.getRequestURI();
            String query = request.getQueryString();
            String target = TARGET_URL + path + (query != null ? "?" + query : "");

            try {
                URL targetUrl = new URL(target);
                HttpURLConnection conn = (HttpURLConnection) targetUrl.openConnection();
                conn.setRequestMethod(request.getMethod());
                conn.setInstanceFollowRedirects(true);

                // Copy request headers, excluding Accept-Encoding
                String[] headersToCopy = {
                        "Accept", "Connection", "User-Agent", "Accept-Language", "Upgrade-Insecure-Requests"
                };
                for (String header : headersToCopy) {
                    String value = request.getHeader(header);
                    if (value != null) {
                        conn.setRequestProperty(header, value);
                    }
                }
                conn.setRequestProperty("Host", targetUrl.getHost());
                conn.setRequestProperty("Accept-Encoding", "identity"); // Force uncompressed
                conn.setRequestProperty("Token", "Cortexiq9k5TJMxRtkpSDlneqQv3fRb5Q1TANZ7V.aSwQQfbsaEqRi.MYUCG9ydWqOs.HY");

                // Handle request body
                if ("POST".equalsIgnoreCase(request.getMethod()) || "PUT".equalsIgnoreCase(request.getMethod())) {
                    conn.setDoOutput(true);
                    try (InputStream is = request.getInputStream();
                         OutputStream os = conn.getOutputStream()) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                    }
                }

                // Set response status
                int statusCode = conn.getResponseCode();
                response.setStatus(statusCode);

                // Set Content-Type based on path
                String contentType = conn.getHeaderField("Content-Type");
                if (path.endsWith(".js")) {
                    contentType = "application/javascript; charset=UTF-8";
                } else if (contentType == null || !contentType.contains("text/html")) {
                    contentType = "text/html; charset=UTF-8";
                }
                response.setContentType(contentType);

                // Copy response headers, excluding problematic ones
                conn.getHeaderFields().forEach((key, values) -> {
                    if (key != null && !key.equalsIgnoreCase("Transfer-Encoding") &&
                            !key.equalsIgnoreCase("Content-Length") && !key.equalsIgnoreCase("Content-Encoding") &&
                            !key.equalsIgnoreCase("Content-Disposition")) {
                        values.forEach(value -> response.addHeader(key, value));
                    }
                });

                // Add CORS for JavaScript
                if (path.endsWith(".js")) {
                    response.addHeader("Access-Control-Allow-Origin", "*");
                }

                // Copy response body
                try (InputStream is = statusCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
                     OutputStream os = response.getOutputStream()) {
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
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
    }
}