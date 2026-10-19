package com.jobproof.modules.jobmatch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobproof.modules.jobmatch.domain.JobMatchModels.RequirementDraft;
import com.jobproof.shared.error.AppException;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class JobMatchTextServiceTest {

    private final JobMatchTextService service = new JobMatchTextService(
            "missing-tesseract", "missing-tessdata", "chi_sim+eng", 10, 20, 24_000_000, 2);

    @Test
    void structuredChineseJdKeepsRequiredAndBonusRequirementsSeparate() {
        var parsed = service.parse(jd());

        assertThat(parsed.title()).isEqualTo("Java 后端工程师");
        assertThat(parsed.company()).isEqualTo("合成科技有限公司");
        assertThat(parsed.requirements()).hasSizeGreaterThanOrEqualTo(6);
        assertThat(parsed.requirements()).anyMatch(item -> item.hardGate() && "MUST".equals(item.priority()));
        assertThat(parsed.requirements()).anyMatch(item -> "BONUS".equals(item.category()));
        assertThat(parsed.conflicts()).isEmpty();
    }

    @Test
    void readsTheHeadlineAndKeepsCoreSkillsWithABonusTailAsRequirements() {
        var parsed = service.parse("""
                数据分析师（2026 届校招） - 拾光电商 - 上海 · 全职
                岗位职责：
                1. 负责用户增长与留存相关的数据分析，搭建并维护核心指标看板，定期输出业务洞察；
                2. 设计并评估 A/B 实验，量化策略效果，输出可执行的优化建议；
                3. 与产品、运营团队紧密协作，定位用户转化与流失的关键环节，推动问题闭环；
                4. 参与数据埋点方案评审与数据质量治理，保证分析口径一致；
                任职要求：
                1. 统计学、数学、计算机、经济学等相关专业，硕士及以上学历；
                2. 熟练使用 SQL 与 Python（Pandas、NumPy），有 Spark 或 Hive 经验者优先；
                3. 掌握回归分析、假设检验、因果推断等常用统计方法，能独立设计实验；
                4. 熟悉 Tableau、Power BI 等至少一种可视化工具；
                5. 有互联网公司数据分析相关实习经历者优先；
                6. 逻辑清晰，沟通表达能力强，能把分析结论讲给非技术同学听懂。
                加分项：有机器学习建模经验，参加过数学建模或数据类竞赛并获奖。
                """);

        assertThat(parsed.title()).isEqualTo("数据分析师（2026 届校招）");
        assertThat(parsed.company()).isEqualTo("拾光电商");
        assertThat(parsed.location()).isEqualTo("上海");
        assertThat(parsed.conflicts()).doesNotContain("JOB_TITLE_MISSING");
        var sql = parsed.requirements().stream().filter(item -> item.text().startsWith("熟练使用 SQL")).findFirst().orElseThrow();
        assertThat(sql.priority()).isNotEqualTo("BONUS");
        assertThat(sql.category()).isEqualTo("SKILL");
        var tools = parsed.requirements().stream().filter(item -> item.text().startsWith("熟悉 Tableau")).findFirst().orElseThrow();
        assertThat(tools.hardGate()).isFalse();
        assertThat(parsed.requirements()).anyMatch(item -> item.text().startsWith("有互联网公司") && "BONUS".equals(item.priority()));
        assertThat(parsed.requirements()).anyMatch(item -> item.text().startsWith("有机器学习建模经验") && "BONUS".equals(item.priority()));
    }

    @Test
    void onlyScreeningConditionsAreHardGates() {
        var parsed = service.parse("""
                岗位名称：数据分析师（2026 届校招）
                公司名称：拾光电商
                岗位职责：
                1. 负责电商业务核心指标的日常监控与分析，搭建并维护数据看板。
                任职要求：
                1. 统计学、数学、计算机或相关专业本科及以上学历，2026 届毕业生。
                2. 熟练使用SQL进行数据查询与分析，熟悉 Python（pandas）或 R 进行数据处理。
                3. 有至少一段数据分析相关实习经历。
                4. 具备良好的沟通表达能力和逻辑思维能力。
                5. 要求逻辑清晰，能独立推进专题分析。
                6. 3 年以上数据分析工作经验。
                7. 持有英语六级证书。
                加分项：
                1. 熟悉 Tableau、Power BI 等可视化工具者优先。
                """);

        Function<String, RequirementDraft> byStart = prefix -> parsed.requirements().stream()
                .filter(item -> item.text().startsWith(prefix)).findFirst().orElseThrow();
        assertThat(byStart.apply("统计学").hardGate()).isTrue();
        assertThat(byStart.apply("统计学").category()).isEqualTo("EDUCATION");
        assertThat(byStart.apply("3 年以上").hardGate()).isTrue();
        assertThat(byStart.apply("持有英语六级").hardGate()).isTrue();
        var internship = byStart.apply("有至少一段");
        assertThat(internship.hardGate()).as("a threshold is a must, not a screening gate").isFalse();
        assertThat(internship.priority()).isEqualTo("MUST");
        for (String soft : new String[] {"具备良好的沟通", "要求逻辑清晰", "熟练使用SQL"}) {
            assertThat(byStart.apply(soft).hardGate()).as(soft).isFalse();
            assertThat(byStart.apply(soft).priority()).as(soft).isEqualTo("IMPORTANT");
        }
        assertThat(byStart.apply("负责电商业务").category()).isEqualTo("RESPONSIBILITY");
        assertThat(byStart.apply("熟悉 Tableau").priority()).isEqualTo("BONUS");
    }

    @Test
    void urlFetcherRejectsLoopbackAndPrivateNetworksBeforeOpeningAConnection() {
        assertThatThrownBy(() -> service.fetch("http://127.0.0.1:8080/internal"))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.reason()).isEqualTo("JD_URL_FORBIDDEN"));
        assertThatThrownBy(() -> service.fetch("http://192.168.1.20/job"))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.reason()).isEqualTo("JD_URL_FORBIDDEN"));
        assertThatThrownBy(() -> service.fetch("file:///etc/passwd"))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.reason()).isEqualTo("JD_URL_INVALID"));
    }

    @Test
    void installedOcrReadsChineseScreenshotAndScannedPdf() throws Exception {
        Path executable = Path.of("C:/Program Files/Tesseract-OCR/tesseract.exe");
        Path tessdata = Path.of(".local-data/ocr/tessdata");
        Assumptions.assumeTrue(Files.isRegularFile(executable)
                && Files.isRegularFile(tessdata.resolve("eng.traineddata"))
                && Files.isRegularFile(tessdata.resolve("chi_sim.traineddata")),
                "固定版本的本地 OCR 模型尚未安装");
        JobMatchTextService installed = new JobMatchTextService(executable.toString(), tessdata.toString(),
                "chi_sim+eng", 45, 3, 24_000_000, 1);
        BufferedImage image = syntheticJdImage();
        byte[] png;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            png = output.toByteArray();
        }
        String screenshotText = installed.extract("java-job.png", png);
        assertThat(compact(screenshotText)).contains("Java", "SpringBoot", "MySQL", "Redis");

        byte[] pdf;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            var scannedImage = LosslessFactory.createFromImage(document, image);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(scannedImage, 24, 220, 548, 308);
            }
            document.save(output);
            pdf = output.toByteArray();
        }
        String scannedPdfText = installed.extract("java-job.pdf", pdf);
        assertThat(compact(scannedPdfText)).contains("Java", "SpringBoot", "MySQL", "Redis");
        image.flush();
    }

    private static BufferedImage syntheticJdImage() {
        BufferedImage image = new BufferedImage(1600, 900, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(Color.BLACK);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(new Font("Microsoft YaHei", Font.BOLD, 54));
        graphics.drawString("Java 后端工程师", 90, 120);
        graphics.setFont(new Font("Microsoft YaHei", Font.PLAIN, 38));
        graphics.drawString("岗位职责：负责订单服务接口设计、测试与稳定交付", 90, 230);
        graphics.drawString("任职要求：熟悉 Spring Boot、MySQL 和 Redis", 90, 320);
        graphics.drawString("具备数据库优化、故障定位和跨团队协作能力", 90, 410);
        graphics.drawString("加分项：具有 Kubernetes 或云原生项目实践", 90, 500);
        graphics.dispose();
        return image;
    }

    private static String compact(String value) {
        return value == null ? "" : value.replaceAll("[\\s·,，。:：]", "");
    }

    private static String jd() {
        return """
                岗位名称: Java 后端工程师
                公司名称: 合成科技有限公司
                工作地点: 上海

                岗位职责:
                1. 负责订单与履约服务的需求分析、领域建模、接口设计和稳定交付，并通过可观测指标持续验证系统质量。
                2. 参与微服务治理、数据库性能优化和线上故障复盘，与产品、测试和平台团队协作推进方案落地。
                3. 建设自动化测试、持续集成和发布检查机制，保证核心交易链路在高并发场景下保持可靠。

                任职要求:
                1. 必须具备本科及以上学历，计算机、软件工程或相关专业，能够独立阅读技术文档并进行方案说明。
                2. 至少具备两年 Java 开发经验，熟悉 Spring Boot、MySQL、Redis 及常见消息队列的工程实践。
                3. 具备数据结构、网络、操作系统和关系数据库基础，能够定位接口延迟、资源竞争与一致性问题。
                4. 具备清晰沟通和跨团队协作能力，能够把业务目标拆解为可验收的技术任务与迭代计划。

                加分项:
                1. 有 Kubernetes、云原生可观测平台或大规模交易系统实践经验者优先。
                2. 有开源贡献、技术文章或可复核的性能优化案例者优先。
                """;
    }
}
