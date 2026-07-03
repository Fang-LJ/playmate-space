const ENV = 'local';

const ENV_CONFIG = {
  local: {
    apiBaseUrl: 'http://127.0.0.1:8080'
  },
  test: {
    apiBaseUrl: ''
  },
  prod: {
    apiBaseUrl: ''
  }
};

function getActiveEnv() {
  return ENV;
}

function getApiBaseUrl() {
  return ENV_CONFIG[ENV].apiBaseUrl;
}

module.exports = {
  getActiveEnv,
  getApiBaseUrl
};
