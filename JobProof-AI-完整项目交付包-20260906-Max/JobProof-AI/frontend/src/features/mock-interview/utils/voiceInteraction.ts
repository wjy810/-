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
