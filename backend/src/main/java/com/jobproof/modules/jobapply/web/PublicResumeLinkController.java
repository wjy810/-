package com.jobproof.modules.jobapply.web;

import java.time.Duration;
import org.springframework.http.ResponseCookie;
import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.jobapply.application.ResumeLinkService;
import com.jobproof.modules.jobapply.application.ResumeLinkService.SharedResumeView;
import com.jobproof.modules.jobapply.application.ResumeLinkService.ViewStart;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What anyone holding a resume link can reach: the resume itself, and the receipt the page sends back.
 * Nothing here is indexed or cached.
 */
@RestController
@RequestMapping("/api/v1/public/resume-links/{token}")
public class PublicResumeLinkController {

    private final ResumeLinkService links;

    public PublicResumeLinkController(ResumeLinkService links) {
        this.links = links;
    }

    @GetMapping
    public ApiResponse<SharedResumeView> open(@PathVariable String token, HttpServletResponse response) {
        unlisted(response);
        return ApiResponse.ok(links.open(token, SecurityConfig.currentAccountOrNull()));
    }

    @PostMapping("/views")
    public ApiResponse<ViewStart> view(@PathVariable String token, @RequestBody(required = false) ViewRequest request,
            HttpServletRequest servletRequest, HttpServletResponse response) {
        unlisted(response);
        String reader = OwnerDeviceCookie.read(servletRequest, READER_COOKIE);
        if (reader == null) {
            reader = OwnerDeviceCookie.newToken();
            // Scoped to the share API: it tells a returning reader from a new one and is useless anywhere else.
            response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(READER_COOKIE, reader).httpOnly(true)
                    .secure(servletRequest.isSecure()).sameSite("Lax").path("/api/v1/public/resume-links")
                    .maxAge(Duration.ofDays(365)).build().toString());
        }
        return ApiResponse.ok(links.recordView(token, SecurityConfig.currentAccountOrNull(), reader,
                OwnerDeviceCookie.read(servletRequest, OwnerDeviceCookie.NAME),
                servletRequest.getHeader(HttpHeaders.USER_AGENT), request == null ? null : request.referrer()));
    }

    @PostMapping("/views/{viewId}/heartbeat")
    public ApiResponse<Map<String, Boolean>> heartbeat(@PathVariable String token, @PathVariable String viewId,
            @RequestBody(required = false) HeartbeatRequest request, HttpServletResponse response) {
        unlisted(response);
        if (request != null) links.heartbeat(token, viewId, request.viewKey(), request.seconds());
        return ApiResponse.ok(Map.of("ok", true));
    }

    static final String READER_COOKIE = "jp_reader";

    private static void unlisted(HttpServletResponse response) {
        response.setHeader("X-Robots-Tag", "noindex, nofollow");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    public record ViewRequest(String referrer) {
    }

    public record HeartbeatRequest(String viewKey, int seconds) {
    }
}
