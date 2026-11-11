const abs = require("../../../webpack/helpers")
const { merge } = require("webpack-merge");
const MiniCssExtractPlugin = require('mini-css-extract-plugin')

function projPath(relative) {
    return abs(`packages/themes/viclass/themes/${relative}`)
}

module.exports = {
    mode: 'development',
    entry: {
        "vi.theme": projPath('src/common.scss')
    },
    output: {
        path: abs('dist/viclass/themes'),
    },
    module: {
        rules: [
            {
                test: /\.s[ca]ss$/i,
                use: [
                    MiniCssExtractPlugin.loader,
                    'css-loader',
                    {
                        loader: 'resolve-url-loader',
                    },
                    {
                        loader: 'postcss-loader',
                        options: {
                            postcssOptions: {
                                plugins: [
                                    'postcss-import',
                                    'tailwindcss',
                                    'autoprefixer'
                                ]
                            },
                            sourceMap: true
                        }
                    },
                    {
                        loader: 'sass-loader',
                        options: {
                            sourceMap: true
                        }
                    }
                ]
            },
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
}

