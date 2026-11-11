const path = require('path')
const abs = require("./helpers")

module.exports = {
    mode: 'development',

    entry: {
        index: path.resolve(__dirname, 'index.js'),
    },

    devServer: {
        headers: {
            'Access-Control-Allow-Origin': '*',
            'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, PATCH, OPTIONS',
            'Access-Control-Allow-Headers': 'X-Requested-With, content-type, Authorization',
        },
        port: 3001,
        allowedHosts: 'all',
        static: [
            {
                directory: abs('dist/metaticket/portal.order.datacollector'),
                publicPath: '/odcapp',
            },
            {
                directory: abs('dist/metaticket/portal.seatplanner'),
                publicPath: '/planner',
            },
            {
                directory: abs('dist/metaticket/portal.admin'),
                publicPath: '/',
            },

            // MODULES: container and wrapper for metaticket
            {
                directory: abs('dist/metaticket/mfe'),
                publicPath: '/modules/metaticket/mfe',
            },
            {
                directory: abs('dist/metaticket/ww'),
                publicPath: '/modules/metaticket/ww',
            },

            // --- MODULE: editor.planner and its UI
            {
                directory: abs('dist/metaticket/themes'),
                publicPath: '/modules/metaticket/themes/editor.seatplanner.ui',
            },
            {
                directory: abs('dist/metaticket/mfe'),
                publicPath: '/modules/metaticket/editor.seatplanner.ui',
            },
            {
                directory: abs('dist/metaticket/mfe'),
                publicPath: '/modules/metaticket/editor.seatplanner',
            },

            // --- MODULE: editor.policydesc

            {
                directory: abs('dist/metaticket/mfe'),
                publicPath: '/modules/metaticket/editor.policydesc',
            },

            // --- MODULE: viclass modules locations
            {
                directory: abs('local_modules/viclass/ww'),
                publicPath: '/modules/viclass/ww',
            },
            {
                directory: abs('local_modules/viclass/mfe'),
                publicPath: '/modules/viclass/mfe',
            },
            {
                directory: abs('local_modules/viclass/themes'),
                publicPath: '/modules/viclass/themes',
            },

            //-----------------------------------
            {
                directory: abs('packages/test'),
                publicPath: '/local-test',
            },
        ],
        historyApiFallback: {
            // if some files are not found, we rewrite it if it matches certain pattern
            index: 'index.html',
            rewrites: [
                { from: /^\/policydesc\/(.*)$/, to: '/policydesc/index.html' },
                { from: /^\/planner\/(.*)$/, to: '/planner/index.html' },
                { from: /^\/odcapp\/(.*)$/, to: '/odcapp/index.html' },
            ],
        },
    },
};
