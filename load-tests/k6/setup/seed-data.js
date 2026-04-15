import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const HEADERS = { 'Content-Type': 'application/json' };

export const options = {
  vus: 1,
  iterations: 1,
};

const CUSTOMERS = [
  { name: 'Load Test User 1', email: 'loadtest1@example.com', phone: '555-0001', address: '1 Test St' },
  { name: 'Load Test User 2', email: 'loadtest2@example.com', phone: '555-0002', address: '2 Test St' },
];

const PRODUCTS = [
  { name: 'Load Test Widget',  description: 'Test product', price: 9.99,  stockQuantity: 9999, category: 'Electronics' },
  { name: 'Load Test Gadget',  description: 'Test product', price: 19.99, stockQuantity: 9999, category: 'Electronics' },
];

export default function () {
  for (const c of CUSTOMERS) {
    const r = http.post(`${BASE_URL}/api/customers`, JSON.stringify(c), { headers: HEADERS });
    check(r, { 'customer seeded': res => res.status === 201 || res.status === 200 });
  }
  for (const p of PRODUCTS) {
    const r = http.post(`${BASE_URL}/api/products`, JSON.stringify(p), { headers: HEADERS });
    check(r, { 'product seeded': res => res.status === 201 || res.status === 200 });
  }
  console.log('Seed data setup complete');
}
