/**
 * This is the script that will be loaded by the host app
 *
 */

MetaTicket = (function (window) {
  const APP_URL = 'http://localhost:3000'
  const APP_ORIGIN = 'http://localhost:3000'
  const MessageAction = {
    UPDATE_IFRAME_SIZE: 'UPDATE_IFRAME_SIZE',
  }

  function init(appContainer) {
    if (!appContainer) {
      console.error(`[MetaTicket] Could not initialise app without container`)
    }

    // create the iframe to point to the app
    const iframe = document.createElement('iframe')
    iframe.src = APP_URL
    iframe.style.width = '100%'
    iframe.style.height = '600px'
    iframe.style.border = 'none'
    iframe.style.overflow = 'hidden'
    iframe.style.borderRadius = '6px'
    iframe.style.transition = 'height 0.3s ease-in-out'

    // append the iframe to the container
    appContainer.appendChild(iframe)

    // register the event listener to receive messages from the iframe
    // to dynamically set the iframe to fit the content height
    window.addEventListener('message', (event) => {
      if (event.origin !== APP_ORIGIN) {
        return
      }

      const { data } = event

      if (data.identifier !== 'metaticket') {
        return
      }

      if (data.action === MessageAction.UPDATE_IFRAME_SIZE) {
        console.log(iframe.style.height, data.height)
        iframe.style.height = `${data.height}px`
      }
    })
  }

  return {
    init,
  }
})(window)
