const ModuleFederationPlugin = require("webpack/lib/container/ModuleFederationPlugin");
const { merge } = require('webpack-merge');
const commonBase = require('../../../webpack/webpack.common.base')
const wpshare = require("../../../webpack/webpack.mf.wpshare")
const share = wpshare.share

var merged = merge(commonBase, {
  experiments: {
      outputModule: true
  },
  plugins: [
    new ModuleFederationPlugin({
        library: { type: "module" },
        name: "@metaticket/portal.seatplanner",
        shared: {
          ...wpshare["shareAngular"],
          ...wpshare["shareCommon"],
          ...wpshare["shareProto"],
          ...share("@viclass/editor.core", {
            requiredVersion: "*"
          })
        }

    })
  ]
});

module.exports = merged
