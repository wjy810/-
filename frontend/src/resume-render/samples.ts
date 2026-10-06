/**
 * Fictional sample resumes for the template gallery and render regression (docs/phase2/03 §9).
 * Names, companies and numbers are invented; contact details use reserved example domains.
 */

export const SAMPLE_PROFESSIONAL = {
  basics: {
    name: '林晓',
    email: 'linxiao@example.com',
    phone: '138 0000 0000',
    location: '杭州',
    links: ['github.com/linxiao', 'linxiao.example.com'],
  },
  intentions: { targetJob: '高级后端开发工程师' },
  summary: '5 年互联网后端经验，主导过日均 3,000 万请求的交易系统重构。擅长 Java / Go 高并发服务与数据一致性设计，关注 **稳定性与成本**，带过 6 人小组。',
  experiences: [
    {
      company: '云帆科技', role: '后端技术负责人', department: '交易平台部', startDate: '2022-07', current: true, location: '杭州',
      description: '- 主导订单中心从单体拆分为 9 个领域服务，发布频率从每周 1 次提升到每天 3 次\n- 设计基于本地消息表的最终一致性方案，资金类对账差错从月均 40 笔降到 0\n- 推动全链路压测与容量规划，大促峰值 QPS 4.2 万，P99 延迟稳定在 120ms 以内\n- 带领 6 人小组，建立代码评审与故障复盘机制，线上 P1 故障同比下降 62%',
    },
    {
      company: '星河电商', role: '高级开发工程师', startDate: '2020-03', endDate: '2022-06', location: '上海',
      description: '- 负责商品搜索服务，引入 Elasticsearch 分片治理，查询 P95 从 380ms 降至 90ms\n- 实现库存预占与超卖保护，双十一期间超卖率为 0\n- 编写团队的 Java 编码规范与 Code Review 清单，被 4 个业务组采用',
    },
    {
      company: '青禾软件', role: '开发工程师', startDate: '2018-07', endDate: '2020-02', location: '杭州',
      description: '参与 SaaS 进销存系统开发，负责报表与导出模块，支持 200+ 企业客户。',
    },
  ],
  projects: [
    {
      name: '分布式任务调度平台', role: '发起人 · 核心开发', startDate: '2023-03', endDate: '2023-12',
      description: '- 基于时间轮与分片执行，支撑 1.8 万个定时任务，替换原有 Quartz 集群\n- 提供失败重试、告警与执行审计，任务失败定位时间从小时级缩短到分钟级',
    },
    {
      name: '开源项目 fastcache', role: '维护者', startDate: '2021-01', current: true,
      description: 'Go 语言本地缓存库，GitHub 2.1k Star，被多家公司用于生产环境。',
    },
  ],
  education: [
    { school: '浙江大学', major: '计算机科学与技术', degree: '硕士', startDate: '2015-09', endDate: '2018-06', location: '杭州', description: '研究方向：分布式存储；发表 EI 论文 1 篇。' },
    { school: '南京理工大学', major: '软件工程', degree: '本科', startDate: '2011-09', endDate: '2015-06', location: '南京' },
  ],
  skills: [
    { category: '语言与框架', items: ['Java', 'Go', 'Spring Boot', 'Netty', 'gRPC'] },
    { category: '数据与中间件', items: ['MySQL', 'Redis', 'Kafka', 'Elasticsearch', 'TiDB'] },
    { category: '工程与运维', items: ['Kubernetes', 'Prometheus', '全链路压测', 'DDD'] },
  ],
  certificates: [
    { name: '系统架构设计师（高级）', issuer: '工信部', date: '2021-11' },
    { name: 'CKA 认证', issuer: 'CNCF', date: '2022-05' },
  ],
  honors: [
    { name: '年度技术之星', issuer: '云帆科技', date: '2023-12' },
  ],
  languages: [
    { language: '英语', level: '熟练', score: 'CET-6 586' },
  ],
}

export const SAMPLE_CAMPUS = {
  basics: { name: '周以宁', email: 'zhouyining@example.com', phone: '139 0000 0000', location: '武汉', links: ['github.com/yining'] },
  intentions: { targetJob: '数据分析师（校招）' },
  summary: '统计学硕士在读，熟悉 Python 与 SQL，两段数据分析实习，擅长把业务问题拆成可验证的指标。',
  education: [
    { school: '武汉大学', major: '应用统计', degree: '硕士', startDate: '2024-09', endDate: '2026-06', location: '武汉', description: 'GPA 3.8 / 4.0，专业前 5%；主修多元统计、因果推断、机器学习。' },
    { school: '华中师范大学', major: '数学与应用数学', degree: '本科', startDate: '2020-09', endDate: '2024-06', location: '武汉' },
  ],
  experiences: [
    {
      company: '知味外卖', role: '数据分析实习生', startDate: '2025-06', endDate: '2025-09', location: '北京',
      description: '- 搭建新客留存看板，定位首单补贴后 7 日流失的 3 个关键环节\n- 设计 A/B 实验评估优惠券面额，推动策略调整后 ROI 提升 18%',
    },
    { company: '博文咨询', role: '研究助理实习生', startDate: '2024-12', endDate: '2025-03', location: '武汉', description: '完成 2 份行业研究报告的数据清洗与可视化，处理问卷样本 3,600 份。' },
  ],
  projects: [
    { name: '城市共享单车需求预测', role: '组长', startDate: '2025-03', endDate: '2025-06', description: '使用 LightGBM 与时空特征预测站点需求，MAPE 12.4%，获课程最佳项目。' },
  ],
  organizations: [
    { name: '研究生会学术部', role: '部长', startDate: '2024-10', current: true, description: '组织 6 场学术沙龙，累计参与 400+ 人次。' },
  ],
  skills: [
    { category: '数据', items: ['Python', 'SQL', 'Pandas', 'Spark'] },
    { category: '建模', items: ['回归分析', '因果推断', 'LightGBM'] },
    { category: '工具', items: ['Tableau', 'Excel', 'Git'] },
  ],
  honors: [
    { name: '研究生一等学业奖学金', issuer: '武汉大学', date: '2025-10' },
    { name: '全国大学生数学建模竞赛 省一等奖', issuer: '中国工业与应用数学学会', date: '2023-11' },
  ],
  certificates: [{ name: 'CET-6', issuer: '教育部考试中心', date: '2022-06' }],
}

export const SAMPLE_MINIMAL = {
  basics: { name: '陈默', email: 'chenmo@example.com', phone: '137 0000 0000' },
  intentions: { targetJob: '产品运营' },
  experiences: [{ company: '小满文化', role: '运营专员', startDate: '2024-07', current: true, description: '负责公众号与社群运营，粉丝从 2 万增长到 8 万。' }],
  education: [{ school: '湖南大学', major: '新闻学', degree: '本科', startDate: '2020-09', endDate: '2024-06' }],
}

export const SAMPLE_ENGLISH = {
  basics: { name: 'Alex Chen', email: 'alex.chen@example.com', phone: '+1 415 555 0134', location: 'San Francisco, CA', links: ['linkedin.com/in/alexchen', 'github.com/alexchen'] },
  intentions: { targetJob: 'Senior Software Engineer' },
  summary: 'Backend engineer with 6 years building payments and data platforms. Led the migration of a monolith to services handling **40k requests per second**.',
  experiences: [
    {
      company: 'Northwind Payments', role: 'Senior Software Engineer', startDate: '2021-04', current: true, location: 'San Francisco, CA',
      description: '- Led the ledger rewrite to event sourcing; reconciliation errors dropped from 40 a month to zero\n- Designed idempotent retry flows across 9 services, cutting duplicate charges by 99.7%\n- Mentored 4 engineers; introduced design reviews adopted across the org',
    },
    {
      company: 'Contoso Analytics', role: 'Software Engineer', startDate: '2018-07', endDate: '2021-03', location: 'Seattle, WA',
      description: '- Built the streaming ingestion pipeline (Kafka, Flink) processing 2B events a day\n- Reduced warehouse costs 35% by partition pruning and tiered storage',
    },
  ],
  projects: [
    { name: 'fastcache (open source)', role: 'Maintainer', startDate: '2020-01', current: true, description: 'In-memory cache library for Go with 2.1k GitHub stars, used in production by several companies.' },
  ],
  education: [
    { school: 'University of Washington', major: 'Computer Science', degree: 'B.S.', startDate: '2014-09', endDate: '2018-06', location: 'Seattle, WA' },
  ],
  skills: [
    { category: 'Languages', items: ['Go', 'Java', 'Python', 'SQL'] },
    { category: 'Systems', items: ['Kafka', 'PostgreSQL', 'Redis', 'Kubernetes', 'AWS'] },
  ],
  certificates: [{ name: 'AWS Certified Solutions Architect – Associate', issuer: 'Amazon Web Services', date: '2022-08' }],
}

/** Stress content: many entries and bullets, to exercise pagination and overflow. */
export const SAMPLE_LONG = {
  ...SAMPLE_PROFESSIONAL,
  experiences: [
    ...SAMPLE_PROFESSIONAL.experiences,
    ...SAMPLE_PROFESSIONAL.experiences.map((item, index) => ({ ...item, company: `${item.company}（历史经历 ${index + 1}）` })),
  ],
  projects: [...SAMPLE_PROFESSIONAL.projects, ...SAMPLE_PROFESSIONAL.projects, ...SAMPLE_PROFESSIONAL.projects],
}

export const SAMPLES = {
  professional: SAMPLE_PROFESSIONAL,
  campus: SAMPLE_CAMPUS,
  minimal: SAMPLE_MINIMAL,
  english: SAMPLE_ENGLISH,
  long: SAMPLE_LONG,
} as const

export type SampleId = keyof typeof SAMPLES

export function sampleFor(locale: 'zh-CN' | 'en', category?: string): Record<string, unknown> {
  if (locale === 'en') return SAMPLE_ENGLISH
  return category === 'campus' ? SAMPLE_CAMPUS : SAMPLE_PROFESSIONAL
}
