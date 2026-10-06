import type { Component } from 'vue'
import type { FeatureKey } from '@/shared/api/capabilities'
import {
  Bell,
  Compass,
  Cpu,
  FileText,
  FolderOpen,
  History,
  LayoutDashboard,
  LayoutTemplate,
  Mic,
  PenSquare,
  Settings,
  Shapes,
  Target,
} from 'lucide-vue-next'

export type NavItem = {
  key: string
  to: string
  label: string
  icon: Component
  /** Extra path prefixes that keep this item highlighted. */
  match?: string[]
  shortcut?: string
  badge?: 'unread'
  /** Deployment feature switch; the item is marked "未开放" when it is off. */
  feature?: FeatureKey
}

export type NavGroup = { key: string; label: string; items: NavItem[]; adminOnly?: boolean }

export const NAV_GROUPS: NavGroup[] = [
  {
    key: 'main',
    label: '主要',
    items: [
      { key: 'dashboard', to: '/dashboard', label: '工作台', icon: LayoutDashboard, shortcut: 'G D' },
      { key: 'resumes', to: '/resumes', label: '简历', icon: FileText, match: ['/ai-resume', '/resume-home'], shortcut: 'G R' },
      { key: 'job-match', to: '/job-match', label: '岗位匹配', icon: Target, shortcut: 'G M', feature: 'jobMatch' },
      { key: 'career-planning', to: '/career-planning', label: '职业规划', icon: Compass, shortcut: 'G P', feature: 'careerPlanning' },
      { key: 'mock-interviews', to: '/mock-interviews', label: '模拟面试', icon: Mic, shortcut: 'G I' },
    ],
  },
  {
    key: 'library',
    label: '资料',
    items: [
      { key: 'career-library', to: '/career-library', label: '资料库', icon: FolderOpen, match: ['/onboarding'] },
      { key: 'templates', to: '/resume-templates', label: '模板中心', icon: LayoutTemplate, match: ['/template-assets'] },
    ],
  },
  {
    key: 'system',
    label: '系统',
    items: [
      { key: 'notifications', to: '/notifications', label: '通知', icon: Bell, badge: 'unread' },
      { key: 'updates', to: '/updates', label: '更新日志', icon: History },
      { key: 'account', to: '/account', label: '设置', icon: Settings },
    ],
  },
  {
    key: 'admin',
    label: '管理',
    adminOnly: true,
    items: [
      { key: 'admin-templates', to: '/admin/resume-templates', label: '模板运营', icon: Shapes },
      { key: 'admin-ai', to: '/admin/ai-channels', label: 'AI 通道', icon: Cpu },
      { key: 'admin-changelog', to: '/admin/changelog', label: '更新发布', icon: PenSquare },
    ],
  },
]

export function isNavActive(item: NavItem, path: string): boolean {
  if (path === item.to || path.startsWith(`${item.to}/`)) return true
  return (item.match ?? []).some(prefix => path === prefix || path.startsWith(`${prefix}/`))
}

export function findActiveNav(path: string, groups: NavGroup[] = NAV_GROUPS): NavItem | undefined {
  for (const group of groups) {
    const item = group.items.find(candidate => isNavActive(candidate, path))
    if (item) return item
  }
  return undefined
}
