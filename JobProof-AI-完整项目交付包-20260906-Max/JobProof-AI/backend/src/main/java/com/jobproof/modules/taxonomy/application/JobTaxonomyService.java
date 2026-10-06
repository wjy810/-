package com.jobproof.modules.taxonomy.application;

import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobTaxonomyService implements ApplicationRunner {

    private static final Map<String, CategorySeed> CATEGORIES = categories();
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("FE", "前端开发"), Map.entry("WEB", "前端开发"), Map.entry("WEB前端", "前端开发"),
            Map.entry("BE", "后端开发"), Map.entry("后端", "后端开发"), Map.entry("JAVA后端", "Java"),
            Map.entry("HR", "人力资源"), Map.entry("人力", "人力资源"), Map.entry("护理", "护士/护理"),
            Map.entry("护士", "护士/护理"), Map.entry("财会", "会计"), Map.entry("会计师", "会计"),
            Map.entry("老师", "教师/讲师"), Map.entry("教师", "教师/讲师"), Map.entry("产品", "产品经理"),
            Map.entry("运营", "内容运营"), Map.entry("销售", "销售专员"), Map.entry("UI", "视觉设计"));

    private final JdbcTemplate jdbc;
    private final ClockPort clock;

    public JobTaxonomyService(JdbcTemplate jdbc, ClockPort clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM job_taxonomy_nodes", Integer.class);
        if (count != null && count > 0) return;
        Instant now = clock.now();
        int categoryOrder = 0;
        Map<String, String> jobIds = new LinkedHashMap<>();
        for (Map.Entry<String, CategorySeed> entry : CATEGORIES.entrySet()) {
            String categoryCode = entry.getKey();
            CategorySeed seed = entry.getValue();
            String categoryId = stable("category:" + categoryCode);
            insertNode(categoryId, null, "CATEGORY", categoryCode, seed.label(), seed.label(), categoryCode,
                    categoryOrder++ * 1000, now);
            String groupId = stable("group:" + categoryCode);
            insertNode(groupId, categoryId, "GROUP", categoryCode + "_GENERAL", seed.group(), seed.group(),
                    categoryCode, 0, now);
            int jobOrder = 1;
            for (String job : seed.jobs()) {
                String clean = job.trim();
                if (clean.isEmpty() || jobIds.containsKey(normalize(clean))) continue;
                String id = stable("job:" + normalize(clean));
                String code = "JOB_" + id.replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT);
                insertNode(id, groupId, "JOB", code, clean, normalize(clean), categoryCode, jobOrder++, now);
                jobIds.put(normalize(clean), id);
            }
        }
        for (Map.Entry<String, String> alias : ALIASES.entrySet()) {
            String nodeId = jobIds.get(normalize(alias.getValue()));
            if (nodeId != null) {
                jdbc.update("INSERT INTO job_taxonomy_aliases(id,node_id,alias_name,normalized_alias,language_code,created_at) VALUES(?,?,?,?,?,?)",
                        Ids.newId(), nodeId, alias.getKey(), normalize(alias.getKey()), "zh-CN", now);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<NodeView> tree(String parentId, String keyword) {
        String clean = keyword == null ? "" : normalize(keyword);
        if (!clean.isEmpty()) {
            return jdbc.query("SELECT DISTINCT n.*,p.parent_id AS ancestor_category_id FROM job_taxonomy_nodes n LEFT JOIN job_taxonomy_nodes p ON p.id=n.parent_id LEFT JOIN job_taxonomy_aliases a ON a.node_id=n.id WHERE n.status='PUBLISHED' AND n.node_level='JOB' AND (n.normalized_name LIKE ? OR a.normalized_alias LIKE ?) ORDER BY n.sort_order,n.display_name LIMIT 100",
                    (rs, n) -> view(rs), "%" + clean + "%", "%" + clean + "%");
        }
        if (parentId == null || parentId.isBlank()) {
            return jdbc.query("SELECT n.*,p.parent_id AS ancestor_category_id FROM job_taxonomy_nodes n LEFT JOIN job_taxonomy_nodes p ON p.id=n.parent_id WHERE n.parent_id IS NULL AND n.status='PUBLISHED' ORDER BY n.sort_order,n.display_name",
                    (rs, n) -> view(rs));
        }
        return jdbc.query("SELECT n.*,p.parent_id AS ancestor_category_id FROM job_taxonomy_nodes n LEFT JOIN job_taxonomy_nodes p ON p.id=n.parent_id WHERE n.parent_id=? AND n.status='PUBLISHED' ORDER BY n.sort_order,n.display_name",
                (rs, n) -> view(rs), parentId);
    }

    private void insertNode(String id, String parentId, String level, String code, String display, String normalized,
            String occupation, int order, Instant now) {
        jdbc.update("INSERT INTO job_taxonomy_nodes(id,parent_id,node_level,code,display_name,normalized_name,catalog_occupation_code,status,sort_order,created_at,updated_at) VALUES(?,?,?,?,?,?,?,'PUBLISHED',?,?,?)",
                id, parentId, level, code, display, normalize(normalized), occupation, order, now, now);
    }

    private NodeView view(java.sql.ResultSet rs) throws java.sql.SQLException {
        String id = rs.getString("id");
        String parentId = rs.getString("parent_id");
        String level = rs.getString("node_level");
        String categoryId = switch (level) {
            case "CATEGORY" -> id;
            case "GROUP" -> parentId;
            case "JOB" -> rs.getString("ancestor_category_id");
            default -> null;
        };
        String groupId = "GROUP".equals(level) ? id : "JOB".equals(level) ? parentId : null;
        return new NodeView(id, parentId, categoryId, groupId, level,
                rs.getString("code"), rs.getString("display_name"), rs.getString("catalog_occupation_code"),
                rs.getString("status"));
    }

    private static String stable(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private static Map<String, CategorySeed> categories() {
        Map<String, CategorySeed> out = new LinkedHashMap<>();
        out.put("TECH", seed("技术研发", "软件、数据、算法、测试与运维", "后端开发|Java|Python|Go/Golang|C/C++|C#/.NET|PHP|Node.js|全栈开发|前端开发|JavaScript|HTML5|移动开发|Android|iOS|Unity3D|数据开发|数据架构|数据挖掘|数据仓库|ETL工程师|人工智能|自然语言处理|推荐算法|搜索算法|机器学习|深度学习|图像算法|图像处理|图像识别|语音识别|自动化测试|软件测试|游戏测试|硬件测试|DevOps工程师|IT技术支持|网络工程师|网络安全/系统安全|系统工程师|数据库管理员|嵌入式开发|FPGA开发|单片机|驱动开发|通信技术工程师|售前工程师|售后工程师"));
        out.put("PRODUCT_OPS", seed("产品运营", "产品、项目、内容与用户运营", "产品经理|产品助理|产品运营|内容运营|用户运营|活动运营|数据运营|社区运营|电商运营|游戏运营|客服运营|项目经理|项目助理|商业分析|策略运营"));
        out.put("SALES_SERVICE", seed("销售客服", "销售、渠道与客户服务", "销售专员|销售经理|大客户销售|电话销售|在线销售|渠道销售|外贸销售|医药销售|汽车销售|房地产销售|客户成功|客户经理|客服专员|客服主管|售后服务|导购|商务拓展"));
        out.put("MARKETING_MEDIA", seed("市场传媒", "市场、公关、广告与内容传媒", "市场专员|市场经理|品牌策划|市场策划|活动策划|公关专员|广告策划|编辑|主编|采编|记者|出版发行|自媒体|作者|演员/配音/模特|经纪人|导演/编剧|影视策划|影视制作|主持人/主播/DJ|摄影/摄像|后期制作|视频剪辑|音频编辑|录音/音效"));
        out.put("DESIGN", seed("设计创意", "视觉、交互、工业与空间设计", "视觉设计|交互设计|UI设计|平面设计|广告设计|包装设计|原画设计|游戏界面设计|游戏角色设计|游戏场景设计|动画设计|3D/CAD/制图|服装设计|工业设计|室内设计|珠宝设计|景观设计|建筑设计|城市规划设计|展示设计"));
        out.put("FINANCE", seed("财务金融", "财务、金融、证券与保险", "会计|出纳|财务管理|财务分析|审计|税务|结算|理财顾问|银行柜员|投融资|证券分析|基金|信托|保险顾问|风控|互联网金融|资产管理"));
        out.put("HR_ADMIN", seed("人力行政", "人力资源、行政与管理支持", "人力资源|招聘|培训|薪酬绩效|员工关系|HRBP|组织发展|行政管理|行政专员|经理助理|前台|后勤|商务司机|董事长秘书/助理|CEO/总裁/总经理|区域负责人"));
        out.put("EDUCATION", seed("教育科研", "教师、教研与科研", "教师/讲师|幼教|小学教师|初中教师|高中教师|大学教师|职业技术教师|外语老师|音乐老师|体育老师|美术老师|助教|培训讲师|课程研发|教育产品研发|教务管理|班主任/辅导员|课程顾问|校长|招生|教学管理|科研人员|科研管理"));
        out.put("MEDICAL", seed("医疗护理", "医疗、护理、药学与健康", "护士/护理|护士长|全科/家庭医生|内科医生|外科医生|儿科医生|牙医|麻醉医生|眼科医生/验光师|中医师|康复治疗师|检验/病理医师|影像/放射科医师|公共卫生/疾控|药剂师|执业药师|药房管理/药师|生物制药|临床研究|临床数据分析|医疗器械研发|医疗器械注册|医学编辑|营养师|理疗师|健身教练|健康顾问"));
        out.put("CONSTRUCTION", seed("建筑工程", "房地产、建筑与工程", "建筑工程师|施工现场管理|工程造价|预结算|工程资料管理|项目监理|结构工程师|高级建筑工程师|房地产项目管理|地产策划|房地产招投标|房产经纪|置业顾问|房地产评估|物业管理|物业投资管理"));
        out.put("LOGISTICS", seed("物流交通", "采购、供应链、仓储与交通", "采购专员|采购工程师|采购管理|买手|进出口贸易|外贸管理|单证员|仓库文员|仓储管理|仓储物料专员|物流管理|物流运营|供应链管理|配送|快递|货运司机|运输经理/主管|货代经理|货代专员|报关员|关务管理|水/空/陆运操作|汽车项目管理|汽车维修|汽车售后服务"));
        out.put("GENERAL", seed("通用其他", "制造、服务与其他通用岗位", "生产管理|生产计划/物料控制|生产设备管理|厂长/经理|机械工程师|机械设计师|机械制图|机电工程师|工业工程师|工艺工程师|材料工程师|质量工程师|质量管理/检测|安全员|设备维修|维修工程师|化工工程师|化学分析|实验室技术员|食品/饮料研发|技术员|文员|酒店管理|餐饮服务|零售店员|旅游服务|职业顾问"));
        return out;
    }

    private static CategorySeed seed(String label, String group, String jobs) {
        return new CategorySeed(label, group, List.of(jobs.split("\\|")));
    }

    private record CategorySeed(String label, String group, List<String> jobs) {}
    public record NodeView(String id, String parentId, String categoryId, String groupId, String level, String code, String displayName,
            String catalogOccupationCode, String status) {}
}
