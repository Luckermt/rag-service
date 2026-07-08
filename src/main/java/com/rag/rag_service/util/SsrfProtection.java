package com.rag.rag_service.util;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class SsrfProtection {

    private static final List<String> ALLOWED_SCHEMES = List.of("http", "https");
    private static final List<String> BLOCKED_IP_PREFIXES = Arrays.asList(
            "10.", "127.", "169.254.", "172.16.", "172.17.", "172.18.", "172.19.",
            "172.20.", "172.21.", "172.22.", "172.23.", "172.24.", "172.25.",
            "172.26.", "172.27.", "172.28.", "172.29.", "172.30.", "172.31.",
            "192.168.", "0.", "::1", "fe80:"
    );

    public void validateUrl(String urlString) {
        try {
            URL url = new URL(urlString);
            if (!ALLOWED_SCHEMES.contains(url.getProtocol().toLowerCase())) {
                throw new IllegalArgumentException("Only HTTP/HTTPS URLs are allowed");
            }

            String host = url.getHost();
            InetAddress inetAddress = InetAddress.getByName(host);
            String ip = inetAddress.getHostAddress();

            if (inetAddress.isLoopbackAddress() || inetAddress.isSiteLocalAddress() ||
                    inetAddress.isLinkLocalAddress()) {
                throw new IllegalArgumentException("Access to private/local IP addresses is not allowed");
            }

            for (String prefix : BLOCKED_IP_PREFIXES) {
                if (ip.startsWith(prefix)) {
                    throw new IllegalArgumentException("Access to private/local IP addresses is not allowed");
                }
            }

        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid URL", e);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Unknown host: " + urlString, e);
        }
    }
}