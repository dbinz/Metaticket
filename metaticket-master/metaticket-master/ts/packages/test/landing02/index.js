class Listener {
    onEvent(data) {
        console.log(data)
    }
}

var wrapper = undefined

let docId = "64de32c0e95c9aaad8393431"

function startApplication() {
    let el = document.getElementById("my-viewport")

    wrapper = new window.metaticket.PlannerWrapper("PolicyDesc")

    wrapper.initialize(el, docId).then(r => {
        console.log("Finish Loading SeatPlan", r)
        wrapper.registerListener(docId, new Listener())

        // simulate an async call that retrieve seats to disabled
        // wrapper.setDisabled(true, docId, ['Tầng 1 | DD40', 'Tầng 1 | DD39'])

        // simulate an async call that retrieve seats to enable
        // setTimeout(() => wrapper.setDisabled(false, docId, ['Tầng 1 | DD40']), 10000)
    })


}

function clearAll() {
    wrapper.clearSelection(docId)
}

function clearSelection() {
    wrapper.clearSelection(docId, ["Tầng 1 | DD40", "Tầng 1 | DD40"])
}

function zoomIn() {
    wrapper.startZoom("zoom-in")
}

function zoomOut() {
    wrapper.startZoom("zoom-out")
}

function pan() {
    wrapper.startPan()
}

function clearTool() {
    wrapper.clearTool()
}
