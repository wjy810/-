package com.jobproof.modules.resume.domain;

import java.util.List;
import java.util.Map;

/**
 * P0A 四套模板只给结构与待填提示，不虚构公司、时间、成果数字或证书。
 */
public final class ResumeTemplates {

    private ResumeTemplates() {
    }

    public static Map<String, String> content(ResumeTemplateCode code) {
        return switch (code) {
            case SOFTWARE_DEV -> Map.of(
                    "education", "待填写学历与专业",
                    "experience", "待填写软件开发相关经历",
                    "projects", "待填写本人参与的软件项目（勿虚构）",
                    "skills", "待填写已掌握的开发技能",
                    "certificates", "待填写已获得的证书，没有则留空",
                    "selfIntro", "待填写自我介绍，仅写本人真实经历");
            case QA -> Map.of(
                    "education", "待填写学历与专业",
                    "experience", "待填写测试相关经历",
                    "projects", "待填写本人参与的测试项目（勿虚构）",
                    "skills", "待填写已掌握的测试技能",
                    "certificates", "待填写已获得的证书，没有则留空",
                    "selfIntro", "待填写自我介绍，仅写本人真实经历");
            case DATA_ANALYSIS -> Map.of(
                    "education", "待填写学历与专业",
                    "experience", "待填写数据分析相关经历",
                    "projects", "待填写本人参与的数据分析项目（勿虚构）",
                    "skills", "待填写已掌握的分析技能",
                    "certificates", "待填写已获得的证书，没有则留空",
                    "selfIntro", "待填写自我介绍，仅写本人真实经历");
            case PRODUCT -> Map.of(
                    "education", "待填写学历与专业",
                    "experience", "待填写产品相关经历",
                    "projects", "待填写本人参与的产品项目（勿虚构）",
                    "skills", "待填写已掌握的产品技能",
                    "certificates", "待填写已获得的证书，没有则留空",
                    "selfIntro", "待填写自我介绍，仅写本人真实经历");
        };
    }

    public static List<KeyOutcome> emptyOutcomes() {
        return List.of();
    }
}
