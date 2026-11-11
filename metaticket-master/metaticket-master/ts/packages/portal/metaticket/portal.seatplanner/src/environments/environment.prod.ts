export const environment = {
    production: true,
};

export function url(path: string) {
    return `/api/ticket/${path}`;
}
