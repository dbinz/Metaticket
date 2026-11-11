export const environment = {
    production: false,
};

export function url(path: string) {
    return `/api/ticket/${path}`;
}
