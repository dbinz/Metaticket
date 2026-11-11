'use client'

import { useEffect, useRef } from 'react'

enum MessageAction {
  UPDATE_IFRAME_SIZE = 'UPDATE_IFRAME_SIZE',
}

// to be extracted or imported from a shared library
const debounce = (fn: Function, ms = 300) => {
  let timeoutId: ReturnType<typeof setTimeout>
  return function (this: any, ...args: unknown[]) {
    clearTimeout(timeoutId)
    timeoutId = setTimeout(() => fn.apply(this, args), ms)
  }
}

const sendHeightToIframeParent = (height: unknown) => {
  // setting targetOrigin to '*' to allow sending messages to different domains
  window.parent.postMessage(
    {
      identifier: 'metaticket',
      action: MessageAction.UPDATE_IFRAME_SIZE,
      height,
    },
    '*'
  )
}

const ResizeObserverComponent = () => {
  useEffect(() => {
    const resizeObserver = new ResizeObserver(
      debounce((entries: ResizeObserverEntry[]) => {
        sendHeightToIframeParent(entries[0].target.clientHeight)
      }, 100)
    )

    resizeObserver.observe(document.body)

    return () => {
      resizeObserver.disconnect()
    }
  }, [])

  return null
}

export { ResizeObserverComponent }
