// common sharing for workspace libraries
const wpdeps = require("../../package.json").dependencies
const { share } = require("@angular-architects/module-federation/webpack")

function vshare(name, options) {

    if (!options) options = {}

    let keys = Object.keys(options)

    if (!keys.includes('strictVersion')) options['strictVersion'] = false
    if (!keys.includes('requiredVersion') && wpdeps[name]) options['requiredVersion'] = wpdeps[name]
    if (!keys.includes('singleton')) options['singleton'] = true

    s = {}
    s[name]=options
    return s
}

module.exports = {
    shareAngular : share({
        ...vshare("@angular/common", {requiredVersion : "auto" }),
        ...vshare("@angular/common/http", {requiredVersion : "auto" }),
        ...vshare("@angular/core", {requiredVersion : "auto" }),

        ...vshare("@angular/cdk/overlay", {requiredVersion : "auto" }),
        ...vshare("@angular/cdk/bidi", {requiredVersion : "auto" }),
        ...vshare("@angular/cdk/portal", {requiredVersion : "auto" }),
        ...vshare("@angular/cdk/scrolling", {requiredVersion : "auto" }),
        ...vshare("@angular/cdk/clipboard", {requiredVersion : "auto" }),
        ...vshare("@angular/cdk/platform", {requiredVersion : "auto" }),
        ...vshare("@angular/cdk/drag-drop", {requiredVersion : "auto" }),
        ...vshare("@angular/material/core", {requiredVersion : "auto" }),
        ...vshare("@angular/material/autocomplete", {requiredVersion : "auto" }),
        ...vshare("@angular/material/dialog", {requiredVersion : "auto" }),
        ...vshare("@angular/material/menu", {requiredVersion : "auto" }),
        ...vshare("@angular/material/button", {requiredVersion : "auto" }),
        ...vshare("@angular/material/select", {requiredVersion : "auto" }),
        ...vshare("@angular/material/slider", {requiredVersion : "auto" }),
        ...vshare("@angular/material/badge", {requiredVersion : "auto" }),
        ...vshare("@angular/material/input", {requiredVersion : "auto" }),
        ...vshare("@angular/material/datepicker", {requiredVersion : "auto" }),
        ...vshare("@angular/material/material-moment-adapter", {requiredVersion : "auto" }),
        ...vshare("@angular/forms", {requiredVersion : "auto" }),

        ...vshare("@angular/platform-browser/animations", {requiredVersion : "auto" }),
        ...vshare("@angular/platform-browser", {requiredVersion : "auto" }),
        ...vshare("@angular/platform-browser-dynamic", {requiredVersion : "auto" }),
    }),
    shareProto : {
        ...vshare("google-protobuf"),
    },
    shareCommon : {
        ...vshare("lodash"),
        ...vshare("jquery"),
        ...vshare("rxjs"),
        ...vshare("axios"),
        ...vshare("zone.js"),
        // ...vshare("tw-elements"),
    },
    shareInternal : {
    },

    share : vshare

}
