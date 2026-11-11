export function collapseElement(element: HTMLElement, toHidePrefix: string, toShowPrefix: string|number) {
    let showEl = element.querySelector(`[id=${toHidePrefix}${toShowPrefix}]`)
    if (showEl == null) return

    let hideEl = element.querySelectorAll(`[id^=${toHidePrefix}]`)
    if (hideEl?.length > 0) {
        hideEl.forEach(el => {
            if (el.id == showEl!.id) {
                if (el.classList.contains( 'hidden' )) el.classList.remove( 'hidden' )
                else el.classList.add( 'hidden' )
            } else {
                if(!el.classList.contains('hidden')) el.classList.add( 'hidden' )
            }
        })
    }
}
