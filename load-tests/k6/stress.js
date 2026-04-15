import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, randomCustomerId, randomProductId, buildOrderPayload, JSON_HEADERS } from './config.js';

export const options = {
  stages: [
    { duration: '1m', target: 100 },
    { duration: '5m', target: 150 },
    { duration: '2m', target: 200 },
    { duration: '2m', target: 0   },
  ],
  thresholds: {
    http_req_failed:   ['rate<0.10'],
    http_req_duration: ['p(99)<5000'],
  },
};

export default function () {
  const r = http.post(
    `${BASE_URL}/api/orders`,
    buildOrderPayload(randomCustomerId(), randomProductId()),
    { headers: JSON_HEADERS }
  );
  check(r, { 'ok': res => res.status < 500 });
  sleep(0.2);
}
