/** @type {import("tailwindcss").Config} */
let baseTail = require("../../../../tailwind.config");
let path = require('path');

module.exports = {
    content: [path.join(__dirname, "src/**/*.{html,js,ts,scss}")],
    theme: baseTail.theme,
    plugins: []
};

