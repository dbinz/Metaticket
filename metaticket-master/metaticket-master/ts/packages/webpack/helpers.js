const path = require('path')

module.exports = function abs(relative) {
    return path.resolve(__dirname, "../..", relative)
}