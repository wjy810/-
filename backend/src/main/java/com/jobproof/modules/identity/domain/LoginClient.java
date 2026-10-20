package com.jobproof.modules.identity.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * The kind of device a session was signed in from, as coarse as "Windows · Chrome": enough to list sign-ins and to
 * tell a new device from a known one, never enough to identify a person. Versions are left out, so a browser
 * update is not a new device.
 */
public record LoginClient(String label) {

    public static final LoginClient UNKNOWN = new LoginClient("未知设备");

    public static LoginClient of(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return UNKNOWN;
        String ua = userAgent;
        List<String> parts = new ArrayList<>(2);
        String system = ua.contains("iPad") ? "iPad"
                : ua.contains("iPhone") ? "iPhone"
                : ua.contains("Android") ? "Android"
                : ua.contains("HarmonyOS") ? "鸿蒙"
                : ua.contains("Windows") ? "Windows"
                : ua.contains("Macintosh") || ua.contains("Mac OS X") ? "Mac"
                : ua.contains("CrOS") ? "ChromeOS"
                : ua.contains("Linux") ? "Linux" : null;
        String browser = ua.contains("MicroMessenger") ? "微信"
                : ua.contains("DingTalk") ? "钉钉"
                : ua.contains("Edg/") || ua.contains("EdgA/") || ua.contains("EdgiOS/") ? "Edge"
                : ua.contains("Firefox/") || ua.contains("FxiOS/") ? "Firefox"
                : ua.contains("OPR/") ? "Opera"
                : ua.contains("Chrome/") || ua.contains("CriOS/") ? "Chrome"
                : ua.contains("Safari/") ? "Safari" : null;
        if (system != null) parts.add(system);
        if (browser != null) parts.add(browser);
        return parts.isEmpty() ? UNKNOWN : new LoginClient(String.join(" · ", parts));
    }
}
