const ModuleFederationPlugin = require("webpack/lib/container/ModuleFederationPlugin");
const { merge } = require("webpack-merge");
const commonBase = require("../../../webpack/webpack.common.base");
const wpshare = require("../../../webpack/webpack.mf.wpshare");

var merged = merge(commonBase, {
    experiments: {
        outputModule: true
    },
    plugins: [
        new ModuleFederationPlugin({
            library: { type: "module" },
            name: "@metaticket/portal.order.datacollector",
            shared: {
                ...wpshare["shareAngular"],
                ...wpshare["shareCommon"],
            }

        })
    ]
});

module.exports = merged;
