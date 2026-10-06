/** Request payload checks; the print page validates the template id again. */

export interface RenderRequest {
  format: 'pdf' | 'png'
  payload: { templateId: string; design?: unknown; content: unknown; photo?: string | null; title?: string }
  options: { pageLimit?: number; pngScale?: number; firstPageOnly?: boolean }
}

export class InvalidRequest extends Error {
  readonly code: string

  constructor(code: string, message: string) {
    super(message)
    this.code = code
  }
}

const TEMPLATE_ID = /^[a-z][a-z0-9-]{1,40}$/
const PHOTO = /^data:image\/(png|jpeg);base64,[A-Za-z0-9+/=]+$/

export function parseRenderRequest(body: unknown): RenderRequest {
  if (!body || typeof body !== 'object') throw new InvalidRequest('INVALID_PAYLOAD', 'body must be a JSON object')
  const raw = body as Record<string, unknown>
  const format = raw.format === 'png' ? 'png' : raw.format === 'pdf' || raw.format === undefined ? 'pdf' : null
  if (!format) throw new InvalidRequest('INVALID_PAYLOAD', 'format must be pdf or png')
  const payload = raw.payload as Record<string, unknown> | undefined
  if (!payload || typeof payload !== 'object') throw new InvalidRequest('INVALID_PAYLOAD', 'payload is required')
  if (typeof payload.templateId !== 'string' || !TEMPLATE_ID.test(payload.templateId)) throw new InvalidRequest('INVALID_PAYLOAD', 'templateId is invalid')
  if (!payload.content || typeof payload.content !== 'object') throw new InvalidRequest('INVALID_PAYLOAD', 'content must be an object')
  if (payload.design !== undefined && payload.design !== null && typeof payload.design !== 'object') throw new InvalidRequest('INVALID_PAYLOAD', 'design must be an object')
  if (payload.photo !== undefined && payload.photo !== null && (typeof payload.photo !== 'string' || !PHOTO.test(payload.photo))) {
    throw new InvalidRequest('INVALID_PAYLOAD', 'photo must be a PNG or JPEG data URL')
  }
  if (payload.title !== undefined && (typeof payload.title !== 'string' || payload.title.length > 200)) throw new InvalidRequest('INVALID_PAYLOAD', 'title is invalid')
  const options = (raw.options && typeof raw.options === 'object' ? raw.options : {}) as Record<string, unknown>
  const pageLimit = typeof options.pageLimit === 'number' && options.pageLimit >= 1 && options.pageLimit <= 5 ? Math.floor(options.pageLimit) : undefined
  const pngScale = typeof options.pngScale === 'number' && options.pngScale > 0 && options.pngScale <= 3 ? options.pngScale : 1
  return {
    format,
    payload: {
      templateId: payload.templateId,
      design: payload.design ?? undefined,
      content: payload.content,
      photo: (payload.photo as string | null | undefined) ?? null,
      title: payload.title as string | undefined,
    },
    options: { pageLimit, pngScale, firstPageOnly: options.firstPageOnly === true },
  }
}
