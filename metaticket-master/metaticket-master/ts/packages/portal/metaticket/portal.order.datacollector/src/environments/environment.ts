export const environment = {
    production: false,
};

export function api(path: string) {
    return `/api/${path}`;
}
