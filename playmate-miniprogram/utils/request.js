const { getApiBaseUrl } = require('./config');
const { getToken, clearToken } = require('./token');

function buildUrl(url) {
  if (/^https?:\/\//.test(url)) {
    return url;
  }
  return `${getApiBaseUrl()}${url}`;
}

function createRequestError(message, code, statusCode, response) {
  const error = new Error(message || '请求失败');
  error.code = code || 'REQUEST_ERROR';
  error.statusCode = statusCode;
  error.response = response;
  return error;
}

function request(options) {
  const {
    url,
    method = 'GET',
    data,
    header = {},
    requireAuth = true,
    showError = true
  } = options || {};

  if (!url) {
    return Promise.reject(createRequestError('请求地址不能为空', 'PARAM_ERROR'));
  }

  const token = getToken();
  const requestHeader = {
    'content-type': 'application/json',
    ...header
  };

  if (requireAuth && token) {
    requestHeader.Authorization = `Bearer ${token}`;
  }

  return new Promise((resolve, reject) => {
    wx.request({
      url: buildUrl(url),
      method,
      data,
      header: requestHeader,
      success(res) {
        const body = res.data || {};
        const responseCode = body.code;

        if (res.statusCode === 401 || responseCode === 'UNAUTHORIZED') {
          clearToken();
          if (showError) {
            wx.showToast({ title: '请先登录', icon: 'none' });
          }
          reject(createRequestError(body.message || '未登录或登录已失效', 'UNAUTHORIZED', res.statusCode, body));
          return;
        }

        if (res.statusCode < 200 || res.statusCode >= 300) {
          if (showError) {
            wx.showToast({ title: body.message || '服务异常', icon: 'none' });
          }
          reject(createRequestError(body.message || '服务异常', responseCode, res.statusCode, body));
          return;
        }

        if (Object.prototype.hasOwnProperty.call(body, 'code')) {
          if (responseCode === 'SUCCESS') {
            resolve(body.data);
            return;
          }
          if (showError) {
            wx.showToast({ title: body.message || '请求失败', icon: 'none' });
          }
          reject(createRequestError(body.message || '请求失败', responseCode, res.statusCode, body));
          return;
        }

        resolve(body);
      },
      fail(err) {
        if (showError) {
          wx.showToast({ title: '网络请求失败', icon: 'none' });
        }
        reject(createRequestError(err.errMsg || '网络请求失败', 'NETWORK_ERROR', undefined, err));
      }
    });
  });
}

module.exports = {
  request
};
