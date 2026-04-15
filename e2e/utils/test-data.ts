export const API_BASE = 'http://localhost:8080';

export const SEED_CUSTOMERS = [
  { id: 1, name: 'John Doe', email: 'john.doe@example.com' },
  { id: 2, name: 'Jane Smith', email: 'jane.smith@example.com' },
  { id: 3, name: 'Bob Johnson', email: 'bob.johnson@example.com' },
];

export const SEED_PRODUCTS = [
  { id: 1, name: 'Laptop', price: 1299.99 },
  { id: 2, name: 'Wireless Mouse', price: 29.99 },
  { id: 3, name: 'Mechanical Keyboard', price: 149.99 },
  { id: 4, name: 'Office Chair', price: 299.99 },
  { id: 5, name: 'Standing Desk', price: 599.99 },
  { id: 6, name: 'Webcam HD', price: 79.99 },
];

export const CREATE_ORDER_PAYLOAD = {
  customerId: 1,
  items: [{ productId: 2, quantity: 1 }],
  shippingAddress: '123 Main St',
  shippingCity: 'New York',
  shippingState: 'NY',
  shippingZip: '10001',
  shippingCountry: 'USA',
};
