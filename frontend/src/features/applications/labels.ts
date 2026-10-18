import type { ApplicationChannel, ApplicationOutcome, ApplicationStage } from './types'

export type StageMeta = { value: ApplicationStage; label: string; hint: string }

/** Board columns, in order. CLOSED gathers every ended application, whatever its outcome. */
export const STAGES: readonly StageMeta[] = [
  { value: 'WISHLIST', label: '想投', hint: '收藏的岗位，还没投出' },
  { value: 'APPLIED', label: '已投递', hint: '等待对方回应' },
  { value: 'ASSESSMENT', label: '笔试 / 测评', hint: '在线测评、笔试、作业' },
  { value: 'INTERVIEW', label: '面试中', hint: '一面到终面' },
  { value: 'OFFER', label: 'Offer', hint: '拿到录用意向' },
  { value: 'CLOSED', label: '已结束', hint: '接受、婉拒或未通过' },
]

export const OUTCOMES: readonly { value: ApplicationOutcome; label: string; tone: 'success' | 'neutral' | 'danger' | 'warning' }[] = [
  { value: 'ACCEPTED', label: '已接受 Offer', tone: 'success' },
  { value: 'DECLINED', label: '婉拒 Offer', tone: 'neutral' },
  { value: 'REJECTED', label: '未通过', tone: 'danger' },
  { value: 'WITHDRAWN', label: '主动放弃', tone: 'neutral' },
  { value: 'NO_RESPONSE', label: '没有回应', tone: 'warning' },
]

export const CHANNELS: readonly { value: ApplicationChannel; label: string }[] = [
  { value: 'OFFICIAL_SITE', label: '官网投递' },
  { value: 'JOB_BOARD', label: '招聘平台' },
  { value: 'REFERRAL', label: '内推' },
  { value: 'CAMPUS', label: '校园招聘' },
  { value: 'EMAIL', label: '邮件投递' },
  { value: 'HEADHUNTER', label: '猎头推荐' },
  { value: 'OTHER', label: '其他' },
]

const STAGE_LABEL = new Map(STAGES.map(stage => [stage.value, stage.label]))
const OUTCOME_LABEL = new Map(OUTCOMES.map(outcome => [outcome.value, outcome.label]))
const CHANNEL_LABEL = new Map(CHANNELS.map(channel => [channel.value, channel.label]))

export const stageLabel = (stage?: string | null) => STAGE_LABEL.get(stage as ApplicationStage) ?? '未知阶段'
export const outcomeLabel = (outcome?: string | null) => OUTCOME_LABEL.get(outcome as ApplicationOutcome) ?? ''
export const outcomeTone = (outcome?: string | null) => OUTCOMES.find(item => item.value === outcome)?.tone ?? 'neutral'
export const channelLabel = (channel?: string | null) => CHANNEL_LABEL.get(channel as ApplicationChannel) ?? '其他'

export function deviceLabel(device?: string | null): string {
  if (device === 'MOBILE') return '手机'
  if (device === 'TABLET') return '平板'
  if (device === 'DESKTOP') return '电脑'
  return '未知设备'
}

export function clientLabel(client?: string | null): string {
  return ({ WECHAT: '微信', WECOM: '企业微信', DINGTALK: '钉钉', FEISHU: '飞书', QQ: 'QQ', BROWSER: '浏览器' } as Record<string, string>)[client ?? ''] ?? '浏览器'
}

/** 0 → "不到 1 秒"; 95 → "1 分 35 秒"; 1800 → "30 分钟". */
export function readTime(seconds: number): string {
  if (seconds <= 0) return '不到 1 秒'
  if (seconds < 60) return `${seconds} 秒`
  const minutes = Math.floor(seconds / 60)
  const rest = seconds % 60
  return rest ? `${minutes} 分 ${rest} 秒` : `${minutes} 分钟`
}
