const abs = require("../../../webpack/helpers")
const ModuleFederationPlugin = require("webpack/lib/container/ModuleFederationPlugin");
const shared = require('../../../webpack/webpack.mf.wpshare')
const share = shared.share

const projectFolder = "packages/mfe/metaticket/mfe"

module.exports = {
    experiments: {
        outputModule: true
    },
    optimization: {
        splitChunks: {
            cacheGroups: {
                rxjsCommon: {
                    test: /[\\/]node_modules[\\/](rxjs)/,
                    name: 'vendors_rxjs',
                    chunks: 'all'
                },
                angularCDK: {
                    test: /[\\/]node_modules[\\/]@angular[\\/]cdk/,
                    name: 'vendors_angular_cdk',
                    chunks: 'all'
                },
                angularMaterial: {
                    test: /[\\/]node_modules[\\/]@angular[\\/]material/,
                    name: 'vendors_angular_material',
                    chunks: 'all'
                },
                polyfills: {
                    test: /[\\/]node_modules[\\/]tslib/,
                    name: 'polyfill_tslib',
                    chunks: 'all'
                },
                angularAnimations: {
                    test: /[\\/]node_modules[\\/]@angular[\\/]animations/,
                    name: 'vendors_angular_animations',
                    chunks: 'all'
                }
            }
        }
    },
    plugins: [
        new ModuleFederationPlugin({
            runtime: false,
            library: { type: "module" },
            name: "editor.policydesc",
            filename: "editor.policydesc.js",
            exposes: {
                "./editor.policydesc": abs(`${projectFolder}/src/editor.policydesc.ts`)
            },
            shared: {
                ...shared["shareCommon"],
                ...shared["shareInternal"],
                ...share("@metaticket/editor.common", {
                    requiredVersion: "*", strictVersion: false
                }),
                ...share("@metaticket/editor.policydesc", {
                    requiredVersion: "*", strictVersion: false
                }),
                ...share("@viclass/editor.core", {
                    requiredVersion: "*", strictVersion: false
                }),

            }
        }),
        new ModuleFederationPlugin({
            runtime: false,
            library: { type: "module" },
            name: "editor.seatplanner",
            filename: "editor.seatplanner.js",
            exposes: {
                "./editor.seatplanner": abs(`${projectFolder}/src/editor.seatplanner.ts`)
            },
            shared: {
                ...shared["shareCommon"],
                ...shared["shareInternal"],
                ...share("@metaticket/editor.common", {
                    requiredVersion: "*", strictVersion: false
                }),
                ...share("@metaticket/editor.seatplanner", {
                  requiredVersion: "*", strictVersion: false
                }),
                ...share("@viclass/editor.core", {
                    requiredVersion: "*", strictVersion: false
                }),

            }
        }),
        new ModuleFederationPlugin({
            runtime: false,
            library: { type: "module" },
            name: "editor.seatplanner.ui",
            filename: "editor.seatplanner.ui.js",
            exposes: {
                "./editor.seatplanner.ui": abs(`${projectFolder}/src/editor.seatplanner.ui.ts`),
            },
            shared: {
                ...shared["shareCommon"],
                ...shared["shareAngular"],
                ...shared["shareInternal"],
                ...share("@viclass/editor.core", {
                    requiredVersion: "*", strictVersion: false
                }),
                ...share("@viclass/editorui.loader", {
                    requiredVersion: "*", strictVersion: false
                }),
                ...share("@metaticket/editor.common", {
                    requiredVersion: "*", strictVersion: false
                }),
                ...share("@metaticket/editor.seatplanner", {
                  requiredVersion: "*", strictVersion: false
                })
            }
        }),

    ]
};
