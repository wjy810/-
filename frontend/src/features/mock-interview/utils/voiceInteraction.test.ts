import assert from 'node:assert/strict'
import test from 'node:test'
import {
  interviewerPitch,
  interviewerVoiceProfile,
  mergeSpeechTranscript,
  pickInterviewerVoice,
} from './voiceInteraction.ts'

test('interviewer voice alternates between female and male for consecutive questions', () => {
  assert.equal(interviewerVoiceProfile(1), 'FEMALE')
  assert.equal(interviewerVoiceProfile(2), 'MALE')
  assert.equal(interviewerVoiceProfile(3), 'FEMALE')
  assert.ok(interviewerPitch('FEMALE') > interviewerPitch('MALE'))
})

test('voice selection prefers a matching Chinese gender voice', () => {
  const voices = [
    { name: 'Microsoft Yunxi Online', lang: 'zh-CN' },
    { name: 'Microsoft Xiaoxiao Online', lang: 'zh-CN' },
    { name: 'English Default', lang: 'en-US' },
  ]
  assert.equal(pickInterviewerVoice(voices, 'FEMALE', 'zh-CN')?.name, 'Microsoft Xiaoxiao Online')
  assert.equal(pickInterviewerVoice(voices, 'MALE', 'zh-CN')?.name, 'Microsoft Yunxi Online')
})

test('voice selection stays in the requested language when a gender-specific voice is unavailable', () => {
  const voices = [
    { name: 'Chinese Default', lang: 'zh-CN' },
    { name: 'English Default', lang: 'en-US' },
  ]
  assert.equal(pickInterviewerVoice(voices, 'MALE', 'zh-CN')?.name, 'Chinese Default')
})

test('speech transcript merges confirmed and interim text without broken Chinese punctuation', () => {
  assert.equal(
    mergeSpeechTranscript('我负责接口开发。', '随后完成性能优化', '并降低延迟'),
    '我负责接口开发。 随后完成性能优化 并降低延迟',
  )
  assert.equal(mergeSpeechTranscript('', '你好 ，世界 。', ''), '你好，世界。')
  assert.equal(mergeSpeechTranscript('同一句回答。', '同一句回答。', ''), '同一句回答。')
})
