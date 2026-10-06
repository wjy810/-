package com.jobproof.modules.aigateway.security;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;

public final class SsrfGuard {
    public interface Resolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private static final List<String> METADATA_HOSTS = List.of(
        "metadata.google.internal", "metadata.azure.internal", "instance-data.ec2.internal");
    private final Resolver resolver;

    public SsrfGuard() {
        this(InetAddress::getAllByName);
    }

    public SsrfGuard(Resolver resolver) {
        this.resolver = resolver;
    }

    public URI validate(URI uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Only HTTPS endpoints are allowed");
        }
        if (uri.getUserInfo() != null || uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("Endpoint must have a valid host and no user info");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.equals("localhost") || host.endsWith(".localhost") || METADATA_HOSTS.contains(host)) {
            throw new IllegalArgumentException("Local and metadata endpoints are forbidden");
        }
        try {
            InetAddress[] addresses = resolver.resolve(host);
            if (addresses.length == 0) {
                throw new IllegalArgumentException("Endpoint host did not resolve");
            }
            for (InetAddress address : addresses) {
                if (!isPublic(address)) {
                    throw new IllegalArgumentException("Endpoint resolves to a private or reserved address");
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Endpoint host cannot be resolved", e);
        }
        return uri;
    }

    public URI validateRedirect(URI previous, URI location) {
        URI resolved = previous.resolve(location);
        validate(resolved);
        if (!resolved.getHost().equalsIgnoreCase(previous.getHost())) {
            throw new IllegalArgumentException("Cross-host redirects are forbidden");
        }
        return resolved;
    }

    private boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
            || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = bytes[0] & 255;
            int second = bytes[1] & 255;
            return !(first == 0 || first == 10 || first == 127
                || (first == 100 && second >= 64 && second <= 127)
                || (first == 169 && second == 254)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168)
                || (first == 198 && (second == 18 || second == 19))
                || first >= 224);
        }
        return address instanceof Inet6Address
            && !((bytes[0] & 0xfe) == 0xfc
            || ((bytes[0] & 255) == 0xfe && (bytes[1] & 0xc0) == 0x80));
    }
}