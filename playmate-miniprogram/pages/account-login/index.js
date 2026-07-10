const { accountLogin, wxLogin } = require('../../services/auth');
const { handleLoginSuccess } = require('../../utils/login-flow');

Page({
  data: {
    loading: false,
    wxLoading: false,
    redirect: '',
    form: {
      account: '',
      password: ''
    }
  },

  onLoad(options) {
    this.setData({
      redirect: options.redirect ? decodeURIComponent(options.redirect) : ''
    });
  },

  handleInput(event) {
    const { field } = event.currentTarget.dataset;
    this.setData({
      [`form.${field}`]: event.detail.value
    });
  },

  validateForm() {
    const { account, password } = this.data.form;
    if (!account.trim()) {
      return '请填写手机号或邮箱';
    }
    if (!password) {
      return '请填写密码';
    }
    if (password.length < 6 || password.length > 64) {
      return '密码长度需为 6-64 位';
    }
    return '';
  },

  async submitLogin() {
    if (this.data.loading) {
      return;
    }
    const errorMessage = this.validateForm();
    if (errorMessage) {
      wx.showToast({ title: errorMessage, icon: 'none' });
      return;
    }

    const { account, password } = this.data.form;
    this.setData({ loading: true });
    try {
      const loginResult = await accountLogin({
        account: account.trim(),
        password
      });
      wx.showToast({ title: '登录成功', icon: 'success' });
      this.goAfterLogin(loginResult);
    } catch (error) {
      wx.showToast({ title: error.message || '登录失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
    }
  },

  async handleWxLogin() {
    if (this.data.wxLoading) {
      return;
    }
    this.setData({ wxLoading: true });
    try {
      const loginResult = await wxLogin();
      wx.showToast({ title: '登录成功', icon: 'success' });
      this.goAfterLogin(loginResult);
    } catch (error) {
      wx.showToast({ title: error.message || '登录失败', icon: 'none' });
    } finally {
      this.setData({ wxLoading: false });
    }
  },

  goRegister() {
    const redirect = encodeURIComponent(this.data.redirect || '');
    wx.redirectTo({
      url: `/pages/account-register/index?redirect=${redirect}`
    });
  },

  goAfterLogin(loginResult) {
    this.goRedirectTarget(handleLoginSuccess(loginResult, { redirect: this.data.redirect }));
  },

  goRedirectTarget(target) {
    if (target === '/pages/activity-list/index' || target === '/pages/mine/index') {
      wx.switchTab({ url: target });
      return;
    }
    wx.redirectTo({ url: target });
  }
});
