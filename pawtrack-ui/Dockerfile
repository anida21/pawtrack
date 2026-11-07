# The API types are generated from ../pawtrack-development/contracts, which is outside this build context.
# Pass it as a named build context:
#   docker build --build-context contracts=../pawtrack-development/contracts -t pawtrack-ui .
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
COPY --from=contracts . /contracts
ENV CONTRACTS_DIR=/contracts
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/pawtrack-ui/browser /usr/share/nginx/html
EXPOSE 4200
HEALTHCHECK --interval=15s --timeout=3s --retries=5 CMD wget -qO- http://localhost:4200/healthz || exit 1
