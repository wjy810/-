/** Fictional sample content for template previews, so a template is judged by its layout, not by an empty page. */
import type { ResumeMasterView } from './types'

export const SAMPLE_RESUME: ResumeMasterView = { id: 'sample', title: '林晓', status: 'DRAFT', version: 1 }

export const SAMPLE_CONTENT: Record<string, unknown> = {
  basics: { name: '林晓', email: 'linxiao@example.com', phone: '138 0000 0000', location: '杭州', links: ['github.com/linxiao'] },
  intentions: { targetJob: '后端开发工程师' },
  summary: '计算机专业应届生，具备 Java 后端实习经验，熟悉 Spring Boot 与 MySQL / Redis，关注接口性能与数据一致性。',
  education: [
    { school: '浙江大学', major: '计算机科学与技术', degree: '本科', startDate: '2022-09', endDate: '2026-06', location: '杭州', description: 'GPA 3.7 / 4.0，专业前 15%；主修数据结构、操作系统、数据库系统。' },
  ],
  experiences: [
    { company: '某电商科技公司', role: '后端开发实习生', startDate: '2025-07', endDate: '2025-10', location: '杭州', description: '负责订单查询接口，引入 Redis 缓存热点数据，P95 延迟从 180ms 降至 60ms。' },
  ],
  projects: [
    { name: '校园二手交易平台', role: '后端负责人', startDate: '2024-03', endDate: '2024-07', description: '基于 Spring Boot + MySQL 实现发布、检索与站内消息，服务 1,200+ 注册用户。' },
  ],
  skills: [
    { category: '后端开发', items: ['Java', 'Spring Boot', 'MySQL', 'Redis'] },
    { category: '工程工具', items: ['Git', 'Docker', 'Linux'] },
  ],
  certificates: [{ name: 'CET-6', issuer: '教育部考试中心', date: '2024-06' }],
}
