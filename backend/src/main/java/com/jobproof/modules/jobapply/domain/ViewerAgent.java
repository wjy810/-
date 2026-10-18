package com.jobproof.modules.jobapply.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What a resume link receipt says about who opened it, read from the user agent only: the kind of
 * device and the app it was opened in. Crawlers and link previewers are recognised so they never
 * count as a reader.
 */
public record ViewerAgent(String device, String client, boolean bot) {

    private static final Pattern BOT = Pattern.compile(
            "bot|spider|crawl|slurp|preview|facebookexternalhit|embedly|curl|wget|python|java/|okhttp|httpclient"
                    + "|go-http|headless|phantom|lighthouse|monitor", Pattern.CASE_INSENSITIVE);

    public static ViewerAgent parse(String userAgent) {
        String agent = userAgent == null ? "" : userAgent;
        if (agent.isBlank() || BOT.matcher(agent).find()) return new ViewerAgent("UNKNOWN", "UNKNOWN", true);
        String lower = agent.toLowerCase(Locale.ROOT);
        String device = lower.contains("ipad") || lower.contains("tablet") ? "TABLET"
                : lower.contains("mobi") || lower.contains("iphone") || lower.contains("android") ? "MOBILE"
                : "DESKTOP";
        String client;
        if (lower.contains("wxwork")) client = "WECOM";
        else if (lower.contains("micromessenger")) client = "WECHAT";
        else if (lower.contains("dingtalk")) client = "DINGTALK";
        else if (lower.contains("lark") || lower.contains("feishu")) client = "FEISHU";
        else if (lower.contains(" qq/") || lower.contains("mqqbrowser")) client = "QQ";
        else client = "BROWSER";
        return new ViewerAgent(device, client, false);
    }
}
