class Listener {
    onEvent(data) {
        console.log(data)
    }
}

var wrapper = undefined

let docId = "65b75b265f2de9b247f224c0"

function startApplication() {
    let el = document.getElementById("my-viewport")

    wrapper = new metaticket.PlannerWrapper('SeatPlanner')

    wrapper.initialize(el, docId).then(() => {
        console.log("Finish Loading SeatPlan")
        wrapper.registerListener(docId, new Listener())

        // simulate an async call that retrieve seats to disabled
        wrapper.setDisabled(true, docId, ['Z1'])

        // simulate an async call that retrieve seats to enable
        // setTimeout(() => wrapper.setDisabled(false, docId, ['Z1']), 10000)
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
