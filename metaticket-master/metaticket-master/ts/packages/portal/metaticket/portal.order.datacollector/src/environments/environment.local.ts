export const environment = {
    production: false,
};

export function api(path: string) {
    return `http://localhost:4032/${path}`;
}
