function maskPhone(phone) {
  const value = String(phone || '');
  if (value.length < 7) {
    return value;
  }
  return `${value.slice(0, 3)}****${value.slice(-4)}`;
}

function resolvePhoneAuthorizationCode(eventDetail, mockPhoneCode, options = {}) {
  const detail = eventDetail || {};
  const errorMessage = String(detail.errMsg || '').toLowerCase();
  if (errorMessage.includes('deny') || errorMessage.includes('fail')) {
    return { cancelled: true, code: '', isMock: false };
  }
  if (options.preferMock && mockPhoneCode) {
    return { cancelled: false, code: mockPhoneCode, isMock: true };
  }
  if (detail.code) {
    return { cancelled: false, code: detail.code, isMock: false };
  }
  return { cancelled: false, code: mockPhoneCode || '', isMock: Boolean(mockPhoneCode) };
}

module.exports = {
  maskPhone,
  resolvePhoneAuthorizationCode
};
