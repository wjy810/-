/** Plain-language names for the codes the template console shows; unknown codes pass through. */
const STATUS: Record<string, string> = {
  DRAFT: '草稿',
  REVIEWING: '待审批',
  TESTING: '测试中',
  APPROVED: '已批准',
  PUBLISHED: '已发布',
  RETIRED: '已下线',
  REJECTED: '已驳回',
  STATIC_REJECTED: '静态检查未通过',
  PASSED: '已通过',
  FAILED: '失败',
  EXTERNAL_REQUIRED: '需外部验证',
  QUEUED: '排队中',
  RUNNING: '进行中',
  PAUSED: '已暂停',
  COMPLETED: '已完成',
  INVENTORIED: '已登记',
  DUPLICATE: '重复文件',
  UNSUPPORTED: '格式不支持',
  ERROR: '出错',
  READY: '就绪',
  VALID: '有效',
  SCANNING: '扫描中',
  QUARANTINED: '已隔离',
  CLEAN: '未检出风险',
  PENDING: '待处理',
  MALWARE_DETECTED: '检出恶意内容',
  MALWARE_SCANNER_UNAVAILABLE: '扫描器不可用',
  MALWARE_SCANNER_ERROR: '扫描出错',
  STORAGE_INTEGRITY_FAILED: '存储校验失败',
  UNCONFIRMED: '待确认',
  INDEPENDENT_DESIGN: '独立设计',
  UNKNOWN: '未知',
}

const SCANNER_DETAIL: Record<string, string> = {
  SCANNER_CONNECTION_FAILED: '连接不上扫描服务',
  SCANNER_NOT_CONFIGURED: '扫描服务未配置',
  SCANNER_NOT_INSTALLED: '扫描程序未安装',
  SCANNER_TIMEOUT: '扫描服务响应超时',
  SCANNER_PROTOCOL_ERROR: '扫描服务返回了无法识别的响应',
  SCANNER_RESPONSE_UNKNOWN: '扫描服务返回了无法识别的响应',
  SCANNER_REPORTED_ERROR: '扫描服务报告了错误',
  SCANNER_EXECUTION_FAILED: '扫描程序运行失败',
  SCANNER_EXCEPTION: '扫描时出现异常',
  SCANNER_INTERNAL_ERROR: '扫描服务内部错误',
  SCANNER_INTERRUPTED: '扫描被中断',
}

export function glossStatus(code?: string | null): string {
  if (!code) return '—'
  return STATUS[code] ?? code
}

/** Why the malware scanner is blocking uploads, in words an operator can act on. */
export function glossScannerDetail(code?: string | null): string {
  if (!code) return '扫描器不可用'
  if (code.startsWith('SCANNER_EXIT_')) return `扫描程序异常退出（${code.slice('SCANNER_EXIT_'.length)}）`
  return SCANNER_DETAIL[code] ?? code
}

const CAPABILITY: Record<string, string> = { SMART_EDITABLE: '智能可编辑', DOCX_DOWNLOAD: 'Word 下载' }
const ASSET_KIND: Record<string, string> = { RESUME: '简历', COVER: '封面', SCHOOL_APPLICATION: '升学申请', COVER_LETTER: '自荐信与范文' }

export function glossCapability(code?: string | null): string {
  return code ? CAPABILITY[code] ?? code : '—'
}

export function glossAssetKind(code?: string | null): string {
  return code ? ASSET_KIND[code] ?? code : '—'
}
