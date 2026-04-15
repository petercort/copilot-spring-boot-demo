export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const CUSTOMERS = [1, 2, 3];
export const PRODUCTS  = [1, 2, 3, 4, 5, 6];

export function randomCustomerId() {
  return CUSTOMERS[Math.floor(Math.random() * CUSTOMERS.length)];
}

export function randomProductId() {
  return PRODUCTS[Math.floor(Math.random() * PRODUCTS.length)];
}

export function buildOrderPayload(customerId, productId, qty = 1) {
  return JSON.stringify({
    customerId,
    items: [{ productId, quantity: qty }],
    shippingAddress: '123 Main St',
    shippingCity: 'New York',
    shippingState: 'NY',
    shippingZip: '10001',
    shippingCountry: 'USA',
  });
}

export const JSON_HEADERS = { 'Content-Type': 'application/json' };

export const DEFAULT_THRESHOLDS = {
  http_req_failed:   ['rate<0.01'],
  http_req_duration: ['p(95)<500'],
};
