export type InterviewerVoiceProfile = 'FEMALE' | 'MALE'

export type VoiceDescriptor = {
  name: string
  lang: string
}

const FEMALE_VOICE = /xiaoxiao|xiaoyi|xiaomeng|xiaomo|xiaorui|xiaoshuang|xiaoqiu|huihui|yaoyao|ting-ting|samantha|zira|female|woman|女/i
const MALE_VOICE = /yunxi|yunjian|yunyang|yunfeng|kangkang|david|mark|male|man|男/i

export function interviewerVoiceProfile(questionNumber: number): InterviewerVoiceProfile {
  return Math.max(1, Math.trunc(questionNumber)) % 2 === 1 ? 'FEMALE' : 'MALE'
}

export function interviewerPitch(profile: InterviewerVoiceProfile): number {
  return profile === 'FEMALE' ? 1.08 : 0.88
}

export function pickInterviewerVoice<T extends VoiceDescriptor>(
  voices: readonly T[],
  profile: InterviewerVoiceProfile,
  languageCode: string,
): T | undefined {
  if (!voices.length) return undefined
  const locale = languageCode.toLocaleLowerCase()
  const language = locale.split('-')[0]
  const sameLocale = voices.filter((voice) => voice.lang.toLocaleLowerCase() === locale)
  const sameLanguage = voices.filter((voice) => voice.lang.toLocaleLowerCase().split('-')[0] === language)
  const candidates = sameLocale.length ? sameLocale : sameLanguage.length ? sameLanguage : [...voices]
  const pattern = profile === 'FEMALE' ? FEMALE_VOICE : MALE_VOICE
  return candidates.find((voice) => pattern.test(voice.name)) ?? candidates[0]
}

export function mergeSpeechTranscript(...parts: Array<string | null | undefined>): string {
  return parts
    .map((part) => part?.trim() ?? '')
    .filter(Boolean)
    .filter((part, index, cleanParts) => index === 0 || part !== cleanParts[index - 1])
    .join(' ')
    .replace(/\s+([，。！？；：、,.!?;:])/g, '$1')
    .replace(/\s+/g, ' ')
    .trim()
}

export type MicrophoneIssue = 'denied' | 'missing' | 'busy' | 'insecure' | 'unknown'

/** Why getUserMedia failed, from the DOMException name browsers use (or the API missing outside HTTPS). */
export function microphoneIssue(error: unknown, hasMediaDevices = true): MicrophoneIssue {
  if (!hasMediaDevices) return 'insecure'
  const name = error && typeof error === 'object' && 'name' in error ? String((error as { name: unknown }).name) : ''
  if (name === 'NotAllowedError' || name === 'PermissionDeniedError') return 'denied'
  if (name === 'SecurityError') return 'insecure'
  if (name === 'NotFoundError' || name === 'DevicesNotFoundError' || name === 'OverconstrainedError') return 'missing'
  if (name === 'NotReadableError' || name === 'TrackStartError' || name === 'AbortError') return 'busy'
  return 'unknown'
}

export const MICROPHONE_ISSUE_TEXT: Record<MicrophoneIssue, { title: string; detail: string }> = {
  denied: { title: '浏览器没有允许使用麦克风', detail: '点击地址栏左侧的权限图标，允许此网站使用麦克风，再回来重新检测。' },
  missing: { title: '没有找到可用的麦克风', detail: '请连接耳机或麦克风后重新检测。' },
  busy: { title: '麦克风正被其他应用占用', detail: '请关闭正在使用麦克风的应用（如会议软件）后重新检测。' },
  insecure: { title: '当前连接不安全，浏览器不允许使用麦克风', detail: '请通过 https 地址打开 JobProof 后再使用语音作答。' },
  unknown: { title: '麦克风暂时无法使用', detail: '可以稍后重新检测。' },
}
