const { getApiBaseUrl } = require('../utils/config');
const { getToken, clearToken } = require('../utils/token');

function createUploadError(message, code, statusCode, response) {
  const error = new Error(message || '上传失败');
  error.code = code || 'UPLOAD_ERROR';
  error.statusCode = statusCode;
  error.response = response;
  return error;
}

function chooseImage() {
  return new Promise((resolve, reject) => {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      success(res) {
        const tempFile = res.tempFiles && res.tempFiles[0];
        if (!tempFile || !tempFile.tempFilePath) {
          reject(createUploadError('未选择图片', 'PARAM_ERROR'));
          return;
        }
        resolve(tempFile.tempFilePath);
      },
      fail(error) {
        reject(createUploadError(error.errMsg || '选择图片失败', 'CHOOSE_IMAGE_ERROR', undefined, error));
      }
    });
  });
}

function uploadActivityCover(filePath) {
  const token = getToken();
  if (!token) {
    return Promise.reject(createUploadError('请先登录', 'UNAUTHORIZED'));
  }

  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: `${getApiBaseUrl()}/api/files/upload`,
      filePath,
      name: 'file',
      header: {
        Authorization: `Bearer ${token}`
      },
      formData: {
        fileType: 'ACTIVITY_COVER'
      },
      success(res) {
        let body;
        try {
          body = JSON.parse(res.data || '{}');
        } catch (error) {
          reject(createUploadError('上传响应解析失败', 'PARSE_ERROR', res.statusCode, res.data));
          return;
        }

        if (res.statusCode === 401 || body.code === 'UNAUTHORIZED') {
          clearToken();
          reject(createUploadError(body.message || '请先登录', 'UNAUTHORIZED', res.statusCode, body));
          return;
        }

        if (res.statusCode < 200 || res.statusCode >= 300) {
          reject(createUploadError(body.message || '上传失败', body.code, res.statusCode, body));
          return;
        }

        if (body.code !== 'SUCCESS') {
          reject(createUploadError(body.message || '上传失败', body.code, res.statusCode, body));
          return;
        }

        resolve(body.data);
      },
      fail(error) {
        reject(createUploadError(error.errMsg || '上传失败', 'NETWORK_ERROR', undefined, error));
      }
    });
  });
}

module.exports = {
  chooseImage,
  uploadActivityCover
};
