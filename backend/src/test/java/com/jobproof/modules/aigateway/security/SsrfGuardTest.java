package com.jobproof.modules.aigateway.security;
import static org.junit.jupiter.api.Assertions.*;import java.net.*;import java.util.Set;import org.junit.jupiter.api.Test;
class SsrfGuardTest {@Test void rejectsProtocolPrivateDnsAndRedirects()throws Exception{SsrfGuard g=new SsrfGuard(h->new InetAddress[]{InetAddress.getByName(h.equals("safe.example")?"93.184.216.34":"169.254.169.254")});assertEquals("safe.example",g.validate(URI.create("https://safe.example/v1")).getHost());assertThrows(IllegalArgumentException.class,()->g.validate(URI.create("http://safe.example/v1")));assertThrows(IllegalArgumentException.class,()->g.validate(URI.create("https://evil.example/v1")));assertThrows(IllegalArgumentException.class,()->g.validateRedirect(URI.create("https://safe.example/v1"),URI.create("https://other.example/x")));}

    @Test
    void proxyResolvedHostsSkipLocalDnsOnlyBehindAProxy() {
        SsrfGuard.Resolver noDns = host -> { throw new UnknownHostException(host); };
        SsrfGuard behindProxy = new SsrfGuard(noDns, Set.of("Gateway.Example"), () -> true);
        assertEquals("gateway.example", behindProxy.validate(URI.create("https://gateway.example/v1")).getHost());
        // Other hosts still need a local answer, and the rest of the rules still apply to listed ones.
        assertThrows(IllegalArgumentException.class, () -> behindProxy.validate(URI.create("https://other.example/v1")));
        assertThrows(IllegalArgumentException.class, () -> behindProxy.validate(URI.create("http://gateway.example/v1")));
        assertThrows(IllegalArgumentException.class, () -> behindProxy.validate(URI.create("https://user@gateway.example/v1")));

        SsrfGuard withoutProxy = new SsrfGuard(noDns, Set.of("gateway.example"), () -> false);
        assertThrows(IllegalArgumentException.class, () -> withoutProxy.validate(URI.create("https://gateway.example/v1")));

        SsrfGuard listedLiteral = new SsrfGuard(noDns, Set.of("10.0.0.5", "localhost"), () -> true);
        assertThrows(IllegalArgumentException.class, () -> listedLiteral.validate(URI.create("https://10.0.0.5/v1")));
        assertThrows(IllegalArgumentException.class, () -> listedLiteral.validate(URI.create("https://localhost/v1")));
    }
}
