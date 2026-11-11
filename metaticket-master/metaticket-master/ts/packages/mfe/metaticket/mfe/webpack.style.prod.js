const devConf = require("./webpack.style")
const {merge} = require("webpack-merge")

module.exports = merge( devConf, {
    mode: "production",
    devtool: false,
    optimization: {
        minimize : true
    },
    plugins: [

    ]
})