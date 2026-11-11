const devConf = require('./webpack.default');
const { merge } = require('webpack-merge');
const TerserJSPlugin = require('terser-webpack-plugin');
const Dotenv = require('dotenv-webpack');

module.exports = merge(devConf, {
    mode: 'production',
    devtool: false,
    optimization: {
        minimizer: [
            new TerserJSPlugin({
                terserOptions: {
                    format: {
                        comments: /webpackIgnore:true/,
                    },
                },
            }),
        ],
    },
    plugins: [
        new Dotenv({
            path: `./.env.prod`,
        }),
    ],
});
