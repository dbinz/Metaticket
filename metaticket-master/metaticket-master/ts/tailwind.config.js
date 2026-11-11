/**
 *  Common tailwind config for all viclass components and websites
 *  Here we mainly define the colors, break points, border, etc...
 *  Projects can import these into its own tailwind
 */
/** @type {import("tailwindcss").Config} */
module.exports = {
    content: [
        "./src/**/*",
        "./node_modules/flowbite/**/*.js"
    ],
    theme: {
        screens: {
            "xs": "320px",
            "sm": "640px",
            "md": "1024px",
            "lg": "1200px",
            "xl": "1920px"
        },
        fontFamily: {
            sans: ["Roboto", "sans-serif"],
            body: ["Roboto", "sans-serif"],
            mono: ["ui-monospace", "monospace"],
        },
        extend: {
            colors: {
                P1: "rgb(var(--P1))",
                P2: "rgb(var(--P2))",
                P3: "rgb(var(--P3))",
                //  Secondary
                SC1: "rgb(var(--SC1))",
                SC2: "rgb(var(--SC2))",
                SC3: "rgb(var(--SC3))",
                SC4: "rgb(var(--SC4))",
                SC5: "rgb(var(--SC5))",
                //  Pastel
                PAS1: "rgb(var(--PAS1))",
                PAS2: "rgb(var(--PAS2))",
                PAS3: "rgb(var(--PAS3))",
                PAS4: "rgb(var(--PAS4))",
                PAS5: "rgb(var(--PAS5))",

                //  Grayscale
                BW1: "rgb(var(--BW1))",
                BW2: "rgb(var(--BW2))",
                BW3: "rgb(var(--BW3))",
                BW4: "rgb(var(--BW4))",
                BW5: "rgb(var(--BW5))",
                BW6: "rgb(var(--BW6))",
                BW7: "rgb(var(--BW7))",
                BW8: "rgb(var(--BW8))",
                //  shadow
                SH1: "var(--SH1)",

                GR1: "var(--GR1)"
            },
            spacing: {
                SP1: "var(--SP1)",
                SP2: "var(--SP2)",
                SP3: "var(--SP3)"
            },
            height: {
                "btn-normal": "var(--btn-normal-height)",
                "btn-small": "var(--btn-small-height)",
                "icon-btn": "var(--icon-btn-height)"
            },
            lineHeight: {
                "btn-normal": "calc(var(--btn-normal-height) - var(--btn-normal-border-width)*2)",
                "btn-small": "calc(var(--btn-small-height) - var(--btn-small-border-width)*2)",
                "icon-btn": "calc(var(--icon-btn-height) - var(--icon-btn-border-width)*2)"
            },
            borderRadius: {
                "btn-normal": "var(--btn-normal-radius)",
                "btn-small": "var(--btn-small-radius)",
                "icon-btn": "var(--icon-btn-radius)"
            },
            borderWidth: {
                "btn-normal": "var(--btn-normal-border-width)",
                "btn-small": "var(--btn-small-border-width)",
                "icon-btn": "var(--icon-btn-border-width)"
            },
            fontSize: {
                "icon-btn": "var(--icon-btn-font-size)"
            }
        }
    },
    plugins: [
        require('flowbite/plugin'),
        {
            'postcss-import': {},
            tailwindcss: {},
            autoprefixer: {},
        }
    ]
};
