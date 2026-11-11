const devConf = require("./webpack.default")
const {merge} = require("webpack-merge")
const Dotenv = require('dotenv-webpack')

module.exports = merge( devConf, {
    plugins: [
        new Dotenv({
            path: `./.env.local`
        }),
    ]
})
