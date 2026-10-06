package com.jobproof.modules.resume.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 定制只出待用户确认稿，不虚构经历，不自动冻结。
 */
public final class ResumeCustomizeHeuristic {

    private ResumeCustomizeHeuristic() {
    }

    public static Map<String, Object> tailor(
            Map<String, Object> masterSnapshot,
            String jobTitle,
            String companyName) {
        Map<String, Object> tailored = new LinkedHashMap<>(masterSnapshot);
        String intro = String.valueOf(masterSnapshot.getOrDefault("selfIntro", ""));
        String prefix = "【待用户确认的定制稿】针对 "
                + nullToUnknown(companyName) + " / "
                + nullToUnknown(jobTitle)
                + "。以下内容来自主档已确认事实，未新增公司、时间、项目或成果数字。";
        tailored.put("selfIntro", prefix + (intro.isBlank() ? "" : "\n" + intro));
        tailored.put("customizeNote", prefix);
        return tailored;
    }

    private static String nullToUnknown(String value) {
        return value == null || value.isBlank() ? "未知岗位信息" : value.trim();
    }
}
