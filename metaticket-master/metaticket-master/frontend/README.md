# Codebase walkthrough

## Getting Started

### Basic commands

```bash
# install deps
yarn
# run development
yarn dev
# run development localhost environment
yarn dev:local
# run development devlocal environment
yarn dev:devlocal
# development build localhost environment (with sourcemap)
yarn build:local
# development build devlocal environment (with sourcemap)
yarn build:devlocal
# production build staging environment (without sourcemap)
yarn build:staging
# production build (without sourcemap)
yarn build:prod
```

### Deploy

Build using `docker:staging` or `docker:prod` command

Run it:

Staging

```
docker build --build-arg env=staging -t staging/metaticket/frontend .
docker run -d -p 4000:3000 --name staging-metaticket-frontend staging/metaticket/frontend
docker start staging-metaticket-frontend
```

Production

```
docker build --build-arg env=prod -t metaticket/frontend .
docker run -d -p 3000:3000 --name metaticket-frontend metaticket/frontend
docker start metaticket-frontend
```

## Tech stack

- React + Typescript
- Next.js
- Tailwind + shadcn-ui (underlying radix-ui for headless UI primitives)
- Data:
    - Axios for http client
    - react-query for handling api lifecycle
    - zod: runtime validation

## Overall structure

- `**src/app/***`
    - Top-level page, following Next.js app router and it’s convention
- `**src/components/***`
    - All components used in the page, **more on this**
- `**src/data/***`
    - Data access layer, containing all stuffs related to calling APIs and interacting with client storage (LocalStorage)
- **`src/lib/*`**
    - Other shared utils

## Data layer

- In `src/data/*`
- Follow BE service structure for easy reference, eg. `data/ticket` → API calls to ticket service
- Some base files:
    - `api.ts`
    - `constants.ts`
    - `storeManager.ts` → create a singleton that help interact with client storage:
        - Scope the storage keys by sessionId
        - Currently use local storage, and following local storage too
- All folders have 2 main files
    - types → all data schemas
    - query → all API calls wrapped by react-query `useQuery` or `useMutation`

## Components

### Main page

- Only 1 main page (index page) as a single page application
- Page renders the different step based on current state

### 3 parent contexts

- Booking context → show & session info for the whole page
- `StepNavContext` → hold state and actions for step navigation
- `StepDataContext` → hold actual booking state for each of the states
    - 3 main parts, one for each of the step: `SeatSelection`, `PurchaseInfo`, `Payment`

These 3 contexts are put at almost top level (page level or in `StepsContainer` → all of the step content components have access to these data.

### Step container & 4 step components

- `StepsContainer` is the parent layout of the booking flow, based on current state (get from `StepNavContext`) → render the correct content
- Each of the 4 step components are under `components/{StepName}/*`, more about each part below

## Styling & theming

- Check out the theme and color overview at `/root_urltheme` page
- Theme are configured by CSS var and tailwind custom classes, checkout `global.css` and `tailwind.config.js` for more detail
- Naming convention follow `shadcn-ui` : [https://ui.shadcn.com/docs/theming#convention](https://ui.shadcn.com/docs/theming#convention)

## Reservation flow

## Payment flow