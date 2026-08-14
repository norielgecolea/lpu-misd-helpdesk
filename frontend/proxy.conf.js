const backendUrl = process.env.BACKEND_URL || 'http://localhost:8080';
const contextPath = process.env.BACKEND_CONTEXT_PATH || '/lpu-helpdesk';

module.exports = {
  [`${contextPath}/api`]: {
    target: backendUrl,
    secure: false,
    changeOrigin: true,
  },
};
