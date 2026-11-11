import type { EditorUILookup } from "@viclass/editorui.loader"
import type { EditorLookup, EditorType, ModuleLookup, ModuleLookupWithSettings } from "@viclass/editor.core";

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

const endPoint = process.env['END_POINT'] ?? window.location.origin;
const editorPolicydescEndPoint = process.env['EDITOR_POLICYDESC_END_POINT'] ?? `${endPoint}/api`
const editorSeatplannerEndPoint = process.env['EDITOR_SEATPLANNER_END_POINT'] ?? `${endPoint}/api`

export const response: MFEConfResponse = {
    descs: [
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
                            id: 4012,
                            channelCode: 4012,
                        },
                        PolicyDesc: {
                            id: 4011,
                            channelCode: 4011,
                        },
                    },
                    viewports: {
                        SeatPlanner: {
                            type: 'board',
                        },
                        PolicyDesc: {
                            type: 'inline',
                        },
                    },
                    tools: [],
                },
            },
        },
        {
            item: 'SeatPlanner',
            impl: {
                editorType: 'SeatPlanner' as EditorType,
                lookup: {
                    remoteName: 'editor.seatplanner',
                    remoteEntry: `${endPoint}/modules/metaticket/editor.seatplanner/editor.seatplanner.js`,
                    exposedModule: './editor.seatplanner',
                },
                settings: {
                    apiUri: `${editorSeatplannerEndPoint}/public/editor/seatplanner`,
                    docViewMode: 'full-viewport',
                    disableEdit: true,
                    disableJsHighlight: false,
                    disableDeselectOnEscape: true,
                },
            },
        },
        {
            item: 'PolicyDesc',
            impl: {
                editorType: 'PolicyDesc' as EditorType,
                lookup: {
                    remoteName: 'editor.policydesc',
                    remoteEntry: `${endPoint}/modules/metaticket/editor.seatplanner/editor.policydesc.js`,
                    exposedModule: './editor.policydesc',
                },
                settings: {
                    apiUri: `${editorPolicydescEndPoint}/public/editor/policydesc`,
                    docViewMode: 'full-viewport',
                    disableEdit: true,
                    disableJsHighlight: false,
                    disableDeselectOnEscape: true,
                },
            },
        },
    ],
};
