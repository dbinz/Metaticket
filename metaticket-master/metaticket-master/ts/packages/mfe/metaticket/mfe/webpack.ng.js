const abs = require("../../../webpack/helpers")
const {merge} = require("webpack-merge");
const mfeConf = require("./webpack.mfe")
const MiniCssExtractPlugin = require('mini-css-extract-plugin')

function projPath(relative) {
    return abs(`packages/mfe/metaticket/mfe/${relative}`)
}

module.exports = merge(mfeConf, {
    output: {
        uniqueName: "@metaticket/mfe",
        publicPath: "auto"
    }
})