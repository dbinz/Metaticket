export const environment = {
  production: false,
  mockShows: true,
  metaDomain: globalThis.location?.origin || 'http://localhost:4200',
  metaPrefix: 'api',
  refreshTokenBeforeExpireInM: 5,
};
