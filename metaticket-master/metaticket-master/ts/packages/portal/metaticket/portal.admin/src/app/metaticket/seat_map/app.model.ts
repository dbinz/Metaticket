import { EditorLookup, ModuleLookup, ModuleLookupWithSettings } from "@viclass/editor.core"
import { EditorUILookup} from "@viclass/editorui.loader"
import { privateApi } from '../../../environments/environment';

export interface MFEDescription {
    item : string
    impl? : EditorLookup | ModuleLookup | ModuleLookupWithSettings
    ui? : EditorUILookup | ModuleLookup
}

export interface MFEConfResponse {
    descs : MFEDescription[]
}

const endPoint = window.location.origin

export const response: MFEConfResponse = {
    descs: [
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
            item: 'CommonToolsEditor',
            ui: {
                editorType: 'CommonToolsEditor',
                uiImpl: {
                    remoteName: 'editorui.commontools',
                    remoteEntry: `${endPoint}/modules/viclass/mfe/editorui.commontools.js`,
                    exposedModule: './editorui.commontools',
                },
                style: {
                    remoteName: 'editorui.commontools.style',
                    remoteEntry: `${endPoint}/modules/viclass/themes/editorui.commontools.style.js`,
                    exposedModule: './editorui.commontools.style',
                },
            },
        },
        {
            item: 'ZoomToolsEditor',
            ui: {
                editorType: 'ZoomToolsEditor',
                uiImpl: {
                    remoteName: 'editorui.commontools',
                    remoteEntry: `${endPoint}/modules/viclass/mfe/editorui.commontools.js`,
                    exposedModule: './editorui.zoomtools',
                },
                style: {
                    remoteName: 'editorui.commontools.style',
                    remoteEntry: `${endPoint}/modules/viclass/themes/editorui.commontools.style.js`,
                    exposedModule: './editorui.commontools.style',
                },
            },
        },
        {
            item: 'EmbedCoordinator',
            impl: {
                lookup: {
                    remoteName: 'coordinator.embed',
                    remoteEntry: `${endPoint}/modules/viclass/mfe/coordinator.embed.js`,
                    exposedModule: './coordinator.embed',
                },
                settings: {
                    editorTypeMapping: {
                        SeatPlanner: {
                            id: 1001,
                            channelCode: 1001,
                        },
                    },
                    viewports: {
                        SeatPlanner: {
                            type: 'board',
                        },
                    },
                    tools: ['history'],
                },
            },
        },
        {
            item: 'SeatPlanner',
            impl: {
                // @ts-ignore
                editorType: 'SeatPlanner',
                lookup: {
                    remoteName: 'editor.seatplanner',
                    remoteEntry: `${endPoint}/modules/metaticket/editor.seatplanner/editor.seatplanner.js`,
                    exposedModule: './editor.seatplanner',
                },
                settings: {
                    apiUri: privateApi('/editor/seatplanner'),
                    docViewMode: 'full-viewport',
                    disableEdit: false,
                    disableJsHighlight: true,
                },
            },
            ui: {
                editorType: 'SeatPlanner',
                uiImpl: {
                    remoteName: 'editor.seatplanner.ui',
                    remoteEntry: `${endPoint}/modules/metaticket/editor.seatplanner.ui/editor.seatplanner.ui.js`,
                    exposedModule: './editor.seatplanner.ui',
                },
                style: {
                    remoteName: 'editor.seatplanner.ui.style',
                    remoteEntry: `${endPoint}/modules/metaticket/themes/editor.seatplanner.ui/editor.seatplanner.ui.style.js`,
                    exposedModule: './editor.seatplanner.ui.style',
                },
            },
        },
    ],
};
