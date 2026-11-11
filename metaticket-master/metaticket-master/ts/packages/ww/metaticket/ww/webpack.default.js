const abs = require("../../../webpack/helpers")
const CopyPlugin = require("copy-webpack-plugin");

const { CleanWebpackPlugin } = require('clean-webpack-plugin');

function projPath(relative) {
    return abs(`packages/ww/metaticket/ww/${relative}`)
}

module.exports = {
    mode: "development",
    devtool: "eval-cheap-source-map",
    entry: {
        "planner.wrapper": projPath("src/planner.wrapper.ts"),
    },
    module: {
        rules: [
            {
                test: /\.tsx?$/,
                use: [{
                    loader: 'ts-loader',
                    options: {
                        compilerOptions: {
                            declaration: true,
                            outDir: abs("dist/metaticket/ww"),
                        }
                    }
                }],
                exclude: /node_modules/,
            },
        ],
    },
    resolve: {
        extensions: ['.tsx', '.ts', '.js'],
    },
    output: {
        path: abs("dist/metaticket/ww"),
        filename: "[name].js",
        scriptType: 'text/javascript',
        library: {
            name: "metaticket",
            type: "assign-properties"
        },
        uniqueName: "@metaticket/ww"
    },
    plugins:[
        new CopyPlugin({
            patterns: [
              {
                from: projPath("package.json"),
                to: abs("dist/metaticket/ww/package.json"),
              }
            ],
        }),
        new CleanWebpackPlugin()
    ]
}
