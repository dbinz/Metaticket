/** @type {import('tailwindcss').Config} */
let path = require("path")
let baseTail = require('../../../../tailwind.config')

module.exports = {
    content: [path.join(__dirname, 'src/**/*.{html,js,ts,scss}')],
    theme: baseTail.theme,
    important: '[draw-seatmap]',
    corePlugins: {
        preflight: false,
    },
    plugins: [],
};

