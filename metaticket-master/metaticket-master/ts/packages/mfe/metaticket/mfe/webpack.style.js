const abs = require("../../../webpack/helpers")
const { merge } = require("webpack-merge");
const MiniCssExtractPlugin = require('mini-css-extract-plugin')
const ModuleFederationPlugin = require("webpack/lib/container/ModuleFederationPlugin");

function projPath(relative) {
    return abs(`packages/mfe/metaticket/mfe/${relative}`)
}

module.exports = merge({
    devtool: "source-map",
    output: {
        uniqueName: "@metaticket/style",
        publicPath: "auto",
        path: abs("dist/metaticket/themes"),
        filename: "[name].js"
    },
    mode: "development",
    entry: {
        main: projPath("src/styles.scss")
    },

    module: {
        rules: [
            {
                test: /\.s[ca]ss$/,
                use: [
                    //MiniCssExtractPlugin.loader,
                    {
                        loader: 'css-loader',
                        options: {
                            exportType: 'css-style-sheet'
                        }
                    },
                    {
                        loader: 'resolve-url-loader',
                    },
                    {
                        loader: 'sass-loader',
                        options: {
                            sourceMap: true,
                        }
                    }
                ]
            }
        ],
    },
    resolve: {
        extensions: ['.tsx', '.ts', '.js', '.sass', '.scss', '.css'],
    },
    plugins: [
        new MiniCssExtractPlugin(
            {
                filename: '[name].css',
                chunkFilename: '[id].css'
            }
        )
    ]
}, {

    experiments: {
        outputModule: true
    },

    plugins: [
        new ModuleFederationPlugin({
            runtime: false,
            library: { type: "module" },
            name: "editor.seatplanner.ui.style",
            filename: "editor.seatplanner.ui.style.js",
            exposes: {
                "./editor.seatplanner.ui.style": projPath(`src/editor.seatplanner.ui.style.scss`)
            },
            shared: {}
        })
    ]

})
