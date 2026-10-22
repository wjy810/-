import { afterEach, describe, expect, it, vi } from 'vitest'
import { createPersonalChannel, testPersonalChannel } from './aiApi'
import type { AiSystemChannelInput } from '../types'
const response = (data: unknown) => new Response(JSON.stringify({ ok: true, data }), { status: 200 })
afterEach(() => vi.unstubAllGlobals())
describe('personal channel command retry identities', () => {
  it('reuses create identity after a lost body and replaces it on changed input', async () => {
    let calls=0
    const fetcher=vi.fn(async () => ++calls===1 ? new Response('{', {status:200}) : response({id:'saved'}))
    vi.stubGlobal('fetch',fetcher)
    const body={name:'Channel A',baseUrl:'https://example.test',apiKey:'synthetic-test-key'} as AiSystemChannelInput
    await expect(createPersonalChannel(body)).rejects.toThrow()
    await createPersonalChannel(body)
    await createPersonalChannel({...body,name:'Channel B'})
    const keys=(fetcher.mock.calls as unknown as [string,RequestInit][]).map(c=>JSON.parse(c[1].body as string).idempotencyKey)
    expect(keys[1]).toBe(keys[0]);expect(keys[2]).not.toBe(keys[0])
  })
  it('reuses capability command identity after transport failure', async () => {
    let calls=0
    const fetcher=vi.fn(async ()=>{if(++calls===1)throw new TypeError('lost');return response({success:true})})
    vi.stubGlobal('fetch',fetcher)
    await expect(testPersonalChannel('retry-channel','model')).rejects.toThrow('lost')
    await testPersonalChannel('retry-channel','model')
    const keys=(fetcher.mock.calls as unknown as [string,RequestInit][]).map(c=>JSON.parse(c[1].body as string).idempotencyKey)
    expect(keys[1]).toBe(keys[0])
  })
})
