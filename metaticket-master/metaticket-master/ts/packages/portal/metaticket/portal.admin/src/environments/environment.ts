export const environment = {
    production: false,
};

export const publicApi = (path: string) => {
    const normalized = path.startsWith('/') ? path.slice(1) : path;
    return `/api/public/${normalized}`;
};

export const privateApi = (path: string) => {
    const normalized = path.startsWith('/') ? path.slice(1) : path;
    return `/private/${normalized}`;
};
