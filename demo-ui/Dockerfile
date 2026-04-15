FROM node:25-alpine

WORKDIR /app

COPY package*.json ./
RUN npm ci --omit=dev

COPY . .

ENV PORT=8090
ENV API_GATEWAY_URL=http://localhost:8080

EXPOSE 8090

CMD ["node", "server.js"]
