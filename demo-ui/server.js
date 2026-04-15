const express = require('express');
const path = require('path');
const { createProxyMiddleware } = require('http-proxy-middleware');

const app = express();
const PORT = process.env.PORT || 8090;
const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://localhost:8080';

// Proxy /api/* to the api-gateway — keeps everything same-origin, no CORS needed
app.use('/api', createProxyMiddleware({
  target: API_GATEWAY_URL,
  changeOrigin: true,
  on: {
    proxyReq: (proxyReq) => {
      // Strip Origin so the gateway doesn't treat this as a cross-origin request.
      // This is a server-to-server call; CORS is not needed here.
      proxyReq.removeHeader('origin');
      proxyReq.removeHeader('referer');
    },
    error: (err, req, res) => {
      console.error('[proxy error]', err.message);
      res.status(502).json({ error: 'API gateway unavailable' });
    },
  },
}));

app.use(express.static(path.join(__dirname, 'public')));

app.listen(PORT, () => {
  console.log(`Demo UI running at http://localhost:${PORT}`);
  console.log(`Proxying /api/* → ${API_GATEWAY_URL}`);
});
