import assert from 'node:assert/strict'
import test from 'node:test'
import { proposalFieldChanges } from './proposalDiff.ts'

test('an update lists only the fields the proposal changes', () => {
  const changes = proposalFieldChanges('UPDATE',
    { title: 'Spring Boot', type: 'SKILL', status: 'LEARNING', detail: { summary: '企业应用', masteryCriteria: ['能写接口'], notes: '我的笔记' } },
    { title: 'Spring Boot', status: 'PLANNED', detail: { summary: '企业应用与自动配置', masteryCriteria: ['能写接口', '能排查启动失败'] } })
  assert.deepEqual(changes, [
    { field: 'status', label: '状态', before: '学习中', after: '计划中' },
    { field: 'summary', label: '摘要', before: '企业应用', after: '企业应用与自动配置' },
    { field: 'masteryCriteria', label: '掌握标准', before: '能写接口', after: '能写接口；能排查启动失败' },
  ])
})

test('an addition shows every proposed field with nothing before it', () => {
  const changes = proposalFieldChanges('ADD', {}, { title: '消息队列', nodeType: 'KNOWLEDGE', detail: { estimatedHours: 6, importance: 'HIGH' } },
    { before: '', after: '后端开发' })
  assert.deepEqual(changes.map(change => [change.label, change.before, change.after]), [
    ['名称', '', '消息队列'], ['类型', '', '知识点'], ['上级', '', '后端开发'], ['预计学时', '', '6 小时'], ['重要度', '', '高'],
  ])
})

test('a move names the old and new parent; relations and deletions have no fields', () => {
  assert.deepEqual(proposalFieldChanges('MOVE', { title: 'SQL' }, {}, { before: '数据能力', after: '后端开发' }),
    [{ field: 'parent', label: '上级', before: '数据能力', after: '后端开发' }])
  assert.deepEqual(proposalFieldChanges('DELETE', { title: 'SQL' }, {}), [])
  assert.deepEqual(proposalFieldChanges('ADD_RELATION', {}, { fromNodeId: 'a', toNodeId: 'b' }), [])
})
