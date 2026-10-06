/**
 * Panels teleported to <body> (job picker, date picker…) are outside any dialog's DOM. A modal dialog
 * disables pointer events and traps focus outside itself, so such panels mark themselves with this
 * attribute: base.css re-enables pointer events on them and UiDialog treats interaction with them
 * as interaction inside the dialog. A panel that closes itself on Esc must `preventDefault()` the
 * event so the dialog underneath stays open.
 */
export const FLOATING_LAYER_ATTR = 'data-floating-layer'

export function isInFloatingLayer(target: EventTarget | null | undefined): boolean {
  return typeof Element !== 'undefined' && target instanceof Element && target.closest(`[${FLOATING_LAYER_ATTR}]`) !== null
}
