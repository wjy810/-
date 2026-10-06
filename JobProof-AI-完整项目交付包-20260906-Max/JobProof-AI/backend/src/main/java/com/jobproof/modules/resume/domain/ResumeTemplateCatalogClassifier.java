package com.jobproof.modules.resume.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ResumeTemplateCatalogClassifier {
    private static final Map<String, String> OCCUPATIONS = occupationRules();
    private static final Map<String, String> STYLES = Map.ofEntries(
            Map.entry("01_表格简历", "表格"), Map.entry("02_简约简历", "简约"),
            Map.entry("03_封面页", "封面"), Map.entry("04_活泼明朗", "活泼"),
            Map.entry("05_简约优雅", "优雅"), Map.entry("06_文艺清新", "文艺"),
            Map.entry("07_稳重大气", "稳重"), Map.entry("08_职业风格", "职业"),
            Map.entry("09_行业专属", "行业"), Map.entry("10_小红书风格", "社交媒体"),
            Map.entry("11_英文简历", "英文"), Map.entry("12_研究生复试", "研究生复试"),
            Map.entry("13_小升初自我介绍", "升学"), Map.entry("14_其他风格", "其他"),
            Map.entry("15_自荐信与范文", "自荐材料"));

    private ResumeTemplateCatalogClassifier() {}

    public static Classification classify(String relativePath, String filename) {
        String normalized = (relativePath + " " + filename).toLowerCase(Locale.ROOT);
        String top = relativePath.replace('\\', '/').split("/", 2)[0];
        String assetKind = switch (top) {
            case "03_封面页" -> "COVER";
            case "13_小升初自我介绍" -> "SCHOOL_APPLICATION";
            case "15_自荐信与范文" -> "COVER_LETTER";
            default -> "RESUME";
        };
        String language = "11_英文简历".equals(top) || normalized.matches(".*\\b(english|resume|cv)\\b.*")
                ? "en" : "zh-CN";
        String pages = pageCount(normalized);
        String photo = normalized.contains("无照片") ? "DISABLED"
                : normalized.contains("有照片") || normalized.contains("照片") ? "OPTIONAL" : "UNSPECIFIED";

        LinkedHashMap<String, String> facets = new LinkedHashMap<>();
        facets.put("STYLE:" + code(STYLES.getOrDefault(top, "其他")), STYLES.getOrDefault(top, "其他"));
        Set<String> occupationLabels = new LinkedHashSet<>();
        Set<String> jobTags = new LinkedHashSet<>();
        OCCUPATIONS.forEach((keyword, category) -> {
            if (normalized.contains(keyword)) {
                occupationLabels.add(category);
                jobTags.add(keyword);
            }
        });
        if (occupationLabels.isEmpty()) occupationLabels.add("通用其他");
        occupationLabels.forEach(label -> facets.put("OCCUPATION:" + occupationCode(label), label));
        jobTags.forEach(label -> facets.put("JOB:" + code(label), label));
        if (normalized.contains("应届") || normalized.contains("校园") || normalized.contains("实习"))
            facets.put("CAREER_STAGE:CAMPUS", "应届/实习");
        if (normalized.contains("研究生") || normalized.contains("复试"))
            facets.put("CAREER_STAGE:GRADUATE", "研究生复试");

        List<Facet> result = new ArrayList<>();
        facets.forEach((key, label) -> {
            String[] parts = key.split(":", 2);
            result.add(new Facet(parts[0], parts[1], label));
        });
        String search = String.join(" ", filename, relativePath, String.join(" ", occupationLabels),
                String.join(" ", jobTags), STYLES.getOrDefault(top, "其他"));
        return new Classification(assetKind, language, pages, photo, search, List.copyOf(result));
    }

    private static String pageCount(String value) {
        if (value.contains("单页")) return "1";
        if (value.contains("双页")) return "2";
        if (value.contains("三页")) return "3";
        if (value.contains("四页")) return "4";
        if (value.contains("五页")) return "5";
        if (value.contains("六页")) return "6";
        if (value.contains("八页")) return "8";
        return null;
    }

    private static String occupationCode(String label) {
        return switch (label) {
            case "技术研发" -> "TECHNOLOGY"; case "产品运营" -> "PRODUCT_OPERATIONS";
            case "销售客服" -> "SALES_SERVICE"; case "市场传媒" -> "MARKETING_MEDIA";
            case "设计创意" -> "DESIGN_CREATIVE"; case "财务金融" -> "FINANCE";
            case "人力行政" -> "HR_ADMIN"; case "教育科研" -> "EDUCATION_RESEARCH";
            case "医疗护理" -> "HEALTHCARE"; case "建筑工程" -> "CONSTRUCTION_ENGINEERING";
            case "物流交通" -> "LOGISTICS_TRANSPORT"; default -> "GENERAL";
        };
    }

    private static String code(String value) {
        return value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9\\p{IsHan}]+", "_");
    }

    private static Map<String, String> occupationRules() {
        LinkedHashMap<String, String> rules = new LinkedHashMap<>();
        add(rules, "技术研发", "开发", "前端", "后端", "工程师", "测试", "数据", "java", "软件", "程序");
        add(rules, "产品运营", "产品", "运营", "电商", "新媒体");
        add(rules, "销售客服", "销售", "客服", "客户经理");
        add(rules, "市场传媒", "市场", "营销", "公关", "传媒", "编辑");
        add(rules, "设计创意", "设计", "视觉", "美术", "音乐", "舞蹈", "艺术");
        add(rules, "财务金融", "财务", "会计", "出纳", "金融", "保险", "理财", "银行");
        add(rules, "人力行政", "人事", "人力", "行政", "管理", "文秘");
        add(rules, "教育科研", "教师", "老师", "幼师", "培训", "教育", "科研", "研究生");
        add(rules, "医疗护理", "护士", "护理", "医生", "医学", "药学", "健康");
        add(rules, "建筑工程", "建筑", "建造", "土木", "工程", "制造");
        add(rules, "物流交通", "物流", "航空", "交通", "供应链", "采购");
        return rules;
    }

    private static void add(Map<String, String> target, String category, String... keywords) {
        for (String keyword : keywords) target.put(keyword, category);
    }

    public record Classification(String assetKind, String languageCode, String pageCount,
            String photoPolicy, String searchText, List<Facet> facets) {}
    public record Facet(String type, String code, String label) {}
}
