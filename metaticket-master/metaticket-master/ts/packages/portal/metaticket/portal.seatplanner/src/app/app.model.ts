import { EditorLookup, ModuleLookup, ModuleLookupWithSettings } from "@viclass/editor.core"
import { EditorUILookup} from "@viclass/editorui.loader"

export interface Environment {
    domain : string
    scheme : 'http' | 'https'
}

export interface MFESpec {
    item : string
    impl? : {
        settings? : any
        useCase? : any
    } | boolean
    ui? : {
        theme? : string
        settings? : any     // sometimes configuration only available at runtime and needs to be merged with the config from server
    } | boolean
}

export interface MFEDescription {
    item : string
    impl? : EditorLookup | ModuleLookup | ModuleLookupWithSettings
    ui? : EditorUILookup | ModuleLookup
}

export interface MFEConfRequest {
    specs : MFESpec[]
}

export interface MFEConfResponse {
    descs : MFEDescription[]
}

export interface MFEConfCreator {
    // needs original request so that we have more information to do the creation, e.g. in switch tool of the common toolbar, we needs to know all the available editor
    (spec : MFESpec, env : Environment, request : MFEConfRequest) : Promise<MFEDescription>
}

// ------------------ WORD EDITOR -----------
export interface MFESpecWordSettings {
    embedded : string[] // list of editor type to be embedded insde
}

// ---------------- Common Tool UI ----------
export interface MFESpecCommonToolUISettings {
    switch : string[] // list of editor type to be switched among
}

const endPoint = window.location.origin;

export const response = {
    "descs": [
        // base ui style will be included directly into the application
        // {
        //     "item": "EditorUIBaseStyle",
        //     "ui": {
        //         "remoteName": "editorui.theme",
        //         "remoteEntry": "/modules/themes/editorui.theme.js",
        //         "exposedModule": "./editorui.theme"
        //     }
        // },
        {
            "item": "CommonToolsEditor",
            "ui": {
                "editorType": "CommonToolsEditor",
                "uiImpl": {
                    "remoteName": "editorui.commontools",
                    "remoteEntry": `${endPoint}/modules/viclass/mfe/editorui.commontools.js`,
                    "exposedModule": "./editorui.commontools"
                },
                "style": {
                    "remoteName": "editorui.commontools.style",
                    "remoteEntry": `${endPoint}/modules/viclass/themes/editorui.commontools.style.js`,
                    "exposedModule": "./editorui.commontools.style"
                }
            }
        },
        {
            "item": "ZoomToolsEditor",
            "ui": {
                "editorType": "ZoomToolsEditor",
                "uiImpl": {
                    "remoteName": "editorui.commontools",
                    "remoteEntry": `${endPoint}/modules/viclass/mfe/editorui.commontools.js`,
                    "exposedModule": "./editorui.zoomtools"
                },
                "style": {
                    "remoteName": "editorui.commontools.style",
                    "remoteEntry": `${endPoint}/modules/viclass/themes/editorui.commontools.style.js`,
                    "exposedModule": "./editorui.commontools.style"
                }
            }
        },
        {
            "item": "EmbedCoordinator",
            "impl": {
                "lookup": {
                    "remoteName": "coordinator.embed",
                    "remoteEntry": `${endPoint}/modules/viclass/mfe/coordinator.embed.js`,
                    "exposedModule": "./coordinator.embed"
                },
                "settings": {
                    "editorTypeMapping": {
                        "SeatPlanner": {
                            "id": 1001,
                            "channelCode": 1001
                        },
                    },
                    "viewports": {
                        "SeatPlanner": {
                            "type": "board"
                        }
                    },
                    "tools": [
                        'history'
                    ]
                }
            }
        },
        {
            "item": "SeatPlanner",
            "impl": {
                "editorType": "SeatPlanner",
                "lookup": {
                    "remoteName": "editor.seatplanner",
                    "remoteEntry": `${endPoint}/modules/metaticket/editor.seatplanner/editor.seatplanner.js`,
                    "exposedModule": "./editor.seatplanner"
                },
                "settings": {
                    "apiUri": `${endPoint}/private/editor/seatplanner`,
                    "docViewMode" : "full-viewport",
                    "disableEdit" : false,
                    "disableJsHighlight" : true
                }
            },
            "ui": {
                "editorType": "SeatPlanner",
                "uiImpl": {
                    "remoteName": "editor.seatplanner.ui",
                    "remoteEntry": `${endPoint}/modules/metaticket/editor.seatplanner.ui/editor.seatplanner.ui.js`,
                    "exposedModule": "./editor.seatplanner.ui"
                },
                "style": {
                    "remoteName": "editor.seatplanner.ui.style",
                    "remoteEntry": `${endPoint}/modules/metaticket/themes/editor.seatplanner.ui/editor.seatplanner.ui.style.js`,
                    "exposedModule": "./editor.seatplanner.ui.style"
                }
            }
        }
    ]
}

export interface TicketGenerationForm {
    showId : "",
    sessionId : "",
    catMapping : {
        [key : string] : {
            catName : string,
            catPrice : number
        }
    }
}
