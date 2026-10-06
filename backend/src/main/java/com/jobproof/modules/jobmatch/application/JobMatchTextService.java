package com.jobproof.modules.jobmatch.application;

import com.jobproof.modules.career.domain.CareerFilePolicy;
import com.jobproof.modules.jobmatch.domain.JobMatchModels.ParsedJd;
import com.jobproof.modules.jobmatch.domain.JobMatchModels.RequirementDraft;
import com.jobproof.modules.resumeimport.domain.ResumeTextExtractor;
import com.jobproof.shared.error.AppException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.jsoup.Jsoup;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JobMatchTextService {
    private static final int MIN_JD = 300;
    private static final int MAX_JD = 10_000;
    private static final int MAX_FETCH_BYTES = 1_500_000;
    private static final Pattern LABEL = Pattern.compile("(?im)^(?:岗位名称|职位名称|岗位|职位)\\s*[:：]\\s*(.{2,80})$");
    private static final Pattern COMPANY = Pattern.compile("(?im)^(?:公司名称|公司)\\s*[:：]\\s*(.{2,100})$");
    private static final Pattern LOCATION = Pattern.compile("(?im)^(?:工作地点|地点|城市)\\s*[:：]\\s*(.{1,80})$");
    private static final Pattern REQUIREMENT_LINE = Pattern.compile("^(?:[-*•]|\\d+[.、)]|[（(]?[一二三四五六七八九十]+[）)、.]?)\\s*(.+)$");
    private static final Set<String> HARD_WORDS = Set.of("必须", "要求", "至少", "本科", "硕士", "年经验", "持有", "具备");
    private static final Set<String> PLUS_WORDS = Set.of("加分", "优先", "更佳", "preferred", "plus");

    private final String executable;
    private final String dataDir;
    private final String languages;
    private final int timeoutSeconds;
    private final int maxPages;
    private final long maxPixels;
    private final Semaphore ocrSlots;

    public JobMatchTextService(
            @Value("${jobproof.job-match.ocr.executable:C:/Program Files/Tesseract-OCR/tesseract.exe}") String executable,
            @Value("${jobproof.job-match.ocr.data-dir:.local-data/ocr/tessdata}") String dataDir,
            @Value("${jobproof.job-match.ocr.languages:chi_sim+eng}") String languages,
            @Value("${jobproof.job-match.ocr.timeout-seconds:45}") int timeoutSeconds,
            @Value("${jobproof.job-match.ocr.max-pages:20}") int maxPages,
            @Value("${jobproof.job-match.ocr.max-pixels:24000000}") long maxPixels,
            @Value("${jobproof.job-match.ocr.max-concurrent:2}") int maxConcurrent) {
        this.executable = executable;
        this.dataDir = dataDir;
        this.languages = languages;
        this.timeoutSeconds = Math.max(10, timeoutSeconds);
        this.maxPages = Math.max(1, Math.min(50, maxPages));
        this.maxPixels = Math.max(1_000_000, Math.min(80_000_000, maxPixels));
        this.ocrSlots = new Semaphore(Math.max(1, Math.min(4, maxConcurrent)), true);
    }

    public boolean ocrAvailable() {
        Path exe = Path.of(executable);
        Path tessdata = Path.of(dataDir);
        return Files.isRegularFile(exe) && Files.isRegularFile(tessdata.resolve("eng.traineddata"))
                && Files.isRegularFile(tessdata.resolve("chi_sim.traineddata"));
    }

    public ParsedJd parse(String raw) {
        String text = normalize(raw);
        int meaningful = text.replaceAll("\\s+", "").length();
        if (meaningful < MIN_JD) {
            throw AppException.user("JD_TEXT_TOO_SHORT", "岗位描述至少需要 300 个有效字符");
        }
        if (text.length() > MAX_JD) {
            throw AppException.user("JD_TEXT_TOO_LARGE", "岗位描述不能超过 10000 字符");
        }
        String title = capture(LABEL, text, "待确认岗位");
        String company = capture(COMPANY, text, "");
        String location = capture(LOCATION, text, "");
        String workMode = containsAny(text.toLowerCase(Locale.ROOT), "远程", "remote") ? "REMOTE"
                : containsAny(text, "混合办公", "混合") ? "HYBRID" : "ONSITE";
        List<RequirementDraft> requirements = new ArrayList<>();
        List<String> responsibilities = new ArrayList<>();
        boolean inResponsibilities = false;
        boolean inPlus = false;
        int lineNo = 0;
        for (String rawLine : text.split("\\n")) {
            lineNo++;
            String line = rawLine.trim();
            if (line.isBlank()) continue;
            String lower = line.toLowerCase(Locale.ROOT);
            if (containsAny(lower, "岗位职责", "工作职责", "职责描述", "responsibilities")) {
                inResponsibilities = true;
                inPlus = false;
                continue;
            }
            if (containsAny(lower, "任职要求", "职位要求", "岗位要求", "requirements", "qualifications")) {
                inResponsibilities = false;
                inPlus = false;
                continue;
            }
            if (containsAny(lower, "加分项", "优先条件", "preferred qualifications")) {
                inResponsibilities = false;
                inPlus = true;
                continue;
            }
            Matcher item = REQUIREMENT_LINE.matcher(line);
            if (!item.matches()) continue;
            String value = item.group(1).trim();
            if (value.length() < 4) continue;
            if (inResponsibilities) {
                responsibilities.add(value);
                requirements.add(new RequirementDraft("RESPONSIBILITY", value, "IMPORTANT", false,
                        "line:" + lineNo, value, 88));
            } else {
                boolean plus = inPlus || containsAny(lower, PLUS_WORDS.toArray(String[]::new));
                boolean hard = !plus && containsAny(lower, HARD_WORDS.toArray(String[]::new));
                String category = inferCategory(value, plus);
                requirements.add(new RequirementDraft(category, value, plus ? "BONUS" : hard ? "MUST" : "IMPORTANT",
                        hard, "line:" + lineNo, value, hard ? 90 : 84));
            }
        }
        if (requirements.size() < 3) {
            for (String sentence : text.split("[。；;]")) {
                String value = sentence.trim();
                if (value.length() < 10 || value.length() > 220) continue;
                boolean plus = containsAny(value.toLowerCase(Locale.ROOT), PLUS_WORDS.toArray(String[]::new));
                boolean hard = !plus && containsAny(value.toLowerCase(Locale.ROOT), HARD_WORDS.toArray(String[]::new));
                requirements.add(new RequirementDraft(inferCategory(value, plus), value,
                        plus ? "BONUS" : hard ? "MUST" : "IMPORTANT", hard,
                        "sentence:" + (requirements.size() + 1), value, 70));
                if (requirements.size() >= 8) break;
            }
        }
        List<RequirementDraft> unique = dedupe(requirements);
        List<String> conflicts = new ArrayList<>();
        if ("待确认岗位".equals(title)) conflicts.add("JOB_TITLE_MISSING");
        if (unique.size() < 3) conflicts.add("REQUIREMENTS_INCOMPLETE");
        int confidence = Math.max(45, Math.min(96, 55 + unique.size() * 4 - conflicts.size() * 12));
        return new ParsedJd(title, company, location, workMode, unique, List.copyOf(responsibilities),
                confidence, List.copyOf(conflicts), text);
    }

    public String extract(String filename, byte[] bytes) {
        CareerFilePolicy.Accepted accepted = CareerFilePolicy.inspect(filename, bytes);
        String type = accepted.contentType();
        if (type.startsWith("image/")) return ocrImage(bytes, accepted.filename());
        String text = ResumeTextExtractor.extract(type, bytes);
        if (meaningful(text) >= 80) return text;
        if ("application/pdf".equals(type)) return ocrPdf(bytes);
        throw AppException.user("JD_FILE_EMPTY", "文件中没有可识别的岗位文字");
    }

    public String fetch(String rawUrl) {
        URI uri;
        try { uri = URI.create(rawUrl == null ? "" : rawUrl.trim()); }
        catch (RuntimeException exception) { throw AppException.user("JD_URL_INVALID", "请输入有效的岗位网页地址"); }
        validatePublic(uri);
        try {
            for (int redirect = 0; redirect < 4; redirect++) {
                InetAddress[] pinned = resolvePublic(uri);
                try (CloseableHttpClient client = pinnedClient(uri.getHost(), pinned)) {
                    HttpGet request = new HttpGet(uri);
                    request.setHeader("User-Agent", "JobProofAI-JobMatch/1.0");
                    request.setHeader("Accept", "text/html,text/plain");
                    FetchResponse fetched = client.execute(request, response -> readFetchResponse(response));
                    int status = fetched.status();
                if (status >= 300 && status < 400) {
                    String location = fetched.location();
                    if (location == null) throw new IOException("redirect without location");
                    URI next = uri.resolve(location);
                    if (next.getHost() == null || !uri.getHost().equalsIgnoreCase(next.getHost())) {
                        throw AppException.user("JD_URL_REDIRECT_BLOCKED", "岗位网页跳转到了其他站点，已停止抓取");
                    }
                    uri = next;
                    continue;
                }
                if (status < 200 || status >= 300) {
                    throw AppException.dependency("JD_URL_FETCH_FAILED", "岗位网页返回 HTTP " + status);
                }
                String contentType = fetched.contentType();
                if (contentType != null && !contentType.startsWith("text/html") && !contentType.startsWith("text/plain")) {
                    throw AppException.user("JD_URL_CONTENT_TYPE_UNSUPPORTED", "岗位网页不是可读取的 HTML 或纯文本");
                }
                byte[] body = fetched.body();
                if (body.length > MAX_FETCH_BYTES) throw AppException.user("JD_URL_TOO_LARGE", "岗位网页内容过大");
                String charset = fetched.charset();
                String html = new String(body, charset == null ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(charset));
                return Jsoup.parse(html, uri.toString()).text();
                }
            }
            throw AppException.user("JD_URL_REDIRECT_LIMIT", "岗位网页重定向次数过多");
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw AppException.dependency("JD_URL_FETCH_FAILED", "岗位网页暂时无法读取，请改用粘贴或文件导入");
        }
    }

    private CloseableHttpClient pinnedClient(String host, InetAddress[] pinned) {
        var manager = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(new DnsResolver() {
                    @Override
                    public InetAddress[] resolve(String candidate) throws java.net.UnknownHostException {
                        InetAddress[] result = candidate.equalsIgnoreCase(host) ? pinned : resolvePublicHost(candidate);
                        if (result.length == 0) throw new java.net.UnknownHostException(candidate);
                        return result;
                    }

                    @Override
                    public String resolveCanonicalHostname(String candidate) {
                        return candidate;
                    }
                })
                .build();
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(8))
                .setConnectionRequestTimeout(Timeout.ofSeconds(8))
                .setResponseTimeout(Timeout.ofSeconds(12))
                .setRedirectsEnabled(false)
                .build();
        return HttpClients.custom().setConnectionManager(manager).setDefaultRequestConfig(requestConfig)
                .disableAutomaticRetries().disableRedirectHandling().build();
    }

    private FetchResponse readFetchResponse(ClassicHttpResponse response) throws IOException {
        HttpEntity entity = response.getEntity();
        byte[] body = entity == null ? new byte[0] : entity.getContent().readNBytes(MAX_FETCH_BYTES + 1);
        ContentType type = entity == null ? null : ContentType.parseLenient(entity.getContentType());
        EntityUtils.consumeQuietly(entity);
        return new FetchResponse(response.getCode(), response.getFirstHeader("Location") == null ? null
                : response.getFirstHeader("Location").getValue(), type == null ? null : type.getMimeType(),
                type == null || type.getCharset() == null ? null : type.getCharset().name(), body);
    }

    private String ocrPdf(byte[] bytes) {
        ensureOcr();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            int pages = Math.min(document.getNumberOfPages(), maxPages);
            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < pages; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 180, ImageType.RGB);
                assertPixelLimit(image);
                result.append(ocrBuffered(image, "resume-page-" + (i + 1))).append('\n');
                image.flush();
            }
            return result.toString().trim();
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw AppException.dependency("OCR_FAILED", "扫描版 PDF 文字识别失败");
        }
    }

    private String ocrImage(byte[] bytes, String filename) {
        ensureOcr();
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) throw new IOException("invalid image");
            assertPixelLimit(image);
            String result = ocrBuffered(image, filename);
            image.flush();
            return result;
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw AppException.dependency("OCR_FAILED", "截图文字识别失败");
        }
    }

    private String ocrBuffered(BufferedImage image, String label) throws IOException, InterruptedException {
        if (!ocrSlots.tryAcquire(2, TimeUnit.SECONDS)) {
            throw AppException.dependency("OCR_BUSY", "文字识别任务较多，请稍后重试");
        }
        Path dir = Files.createTempDirectory("jobproof-ocr-");
        try {
            Path input = dir.resolve("input.png");
            Path output = dir.resolve("result");
            ImageIO.write(image, "png", input.toFile());
            Process process = new ProcessBuilder(executable, input.toString(), output.toString(),
                    "--tessdata-dir", Path.of(dataDir).toAbsolutePath().toString(), "-l", languages,
                    "--psm", "6").redirectErrorStream(true).start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw AppException.dependency("OCR_TIMEOUT", "文字识别超时，请减少页数后重试");
            }
            if (process.exitValue() != 0) {
                throw AppException.dependency("OCR_FAILED", "文字识别失败：" + label);
            }
            return Files.readString(Path.of(output + ".txt"), StandardCharsets.UTF_8);
        } finally {
            try (var files = Files.walk(dir)) {
                files.sorted((a, b) -> b.compareTo(a)).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) {} });
            }
            ocrSlots.release();
        }
    }

    private void assertPixelLimit(BufferedImage image) {
        long pixels = Math.multiplyExact((long) image.getWidth(), (long) image.getHeight());
        if (pixels > maxPixels) throw AppException.user("OCR_IMAGE_TOO_LARGE", "图片像素过大，请压缩到 2400 万像素以内");
    }

    private void ensureOcr() {
        if (!ocrAvailable()) throw AppException.dependency("OCR_UNAVAILABLE", "本地中文 OCR 尚未安装，仍可使用文本型 PDF、DOCX 或粘贴文本");
    }

    private static void validatePublic(URI uri) {
        resolvePublic(uri);
    }

    private static InetAddress[] resolvePublic(URI uri) {
        String scheme = uri == null ? null : uri.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || uri.getHost() == null || uri.getUserInfo() != null) {
            throw AppException.user("JD_URL_INVALID", "岗位网页必须是公开的 HTTP 或 HTTPS 地址");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.equals("localhost") || host.endsWith(".localhost")) {
            throw AppException.user("JD_URL_FORBIDDEN", "不能抓取本机或内网地址");
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (!isPublic(address)) throw AppException.user("JD_URL_FORBIDDEN", "不能抓取本机、内网或保留地址");
            }
            return addresses;
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw AppException.user("JD_URL_UNREACHABLE", "岗位网页域名无法解析");
        }
    }

    private static InetAddress[] resolvePublicHost(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) if (!isPublic(address)) {
                throw new java.net.UnknownHostException("non-public address");
            }
            return addresses;
        } catch (IOException exception) {
            return new InetAddress[0];
        }
    }

    private static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = bytes[0] & 255;
            int second = bytes[1] & 255;
            return !(first == 0 || first == 10 || first == 127 || first >= 224
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 169 && second == 254)
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168));
        }
        return address instanceof Inet6Address && !((bytes[0] & 0xfe) == 0xfc
                || ((bytes[0] & 255) == 0xfe && (bytes[1] & 0xc0) == 0x80));
    }

    private static String normalize(String raw) {
        if (raw == null) return "";
        return raw.replace('\u00a0', ' ').replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
    }

    private static int meaningful(String value) { return value == null ? 0 : value.replaceAll("\\s+", "").length(); }
    private static String capture(Pattern pattern, String text, String fallback) { Matcher m = pattern.matcher(text); return m.find() ? m.group(1).trim() : fallback; }
    private static boolean containsAny(String value, String... needles) { for (String needle : needles) if (value.contains(needle.toLowerCase(Locale.ROOT))) return true; return false; }
    private static String inferCategory(String text, boolean plus) {
        if (plus) return "BONUS";
        String lower = text.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "本科", "硕士", "博士", "学历")) return "EDUCATION";
        if (containsAny(lower, "经验", "年", "项目")) return "EXPERIENCE";
        if (containsAny(lower, "地点", "城市", "出差", "现场", "远程")) return "CONSTRAINT";
        return "SKILL";
    }
    private static List<RequirementDraft> dedupe(List<RequirementDraft> source) {
        Set<String> seen = new LinkedHashSet<>();
        List<RequirementDraft> result = new ArrayList<>();
        for (RequirementDraft item : source) {
            String key = item.text().replaceAll("[\\s，。；;,.]", "").toLowerCase(Locale.ROOT);
            if (key.length() >= 4 && seen.add(key)) result.add(item);
            if (result.size() >= 30) break;
        }
        return List.copyOf(result);
    }

    private record FetchResponse(int status, String location, String contentType, String charset, byte[] body) {}
}
