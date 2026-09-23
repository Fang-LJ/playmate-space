// 发布小程序前切换为 'prod'；本地开发保持 'local'。
const ENV = 'prod';

const ENV_CONFIG = {
  local: {
    apiBaseUrl: 'http://127.0.0.1:8080'
  },
  test: {
    apiBaseUrl: ''
  },
  prod: {
    apiBaseUrl: 'https://api.playmatespace.cloud'
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
