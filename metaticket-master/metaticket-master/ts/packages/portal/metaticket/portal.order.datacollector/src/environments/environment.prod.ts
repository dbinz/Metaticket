export const environment = {
    production: true,
};

export function api(path: string) {
    return `/api/${path}`;
}
