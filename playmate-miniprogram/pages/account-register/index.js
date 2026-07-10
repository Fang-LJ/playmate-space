const { accountRegister } = require('../../services/auth');
const { handleLoginSuccess } = require('../../utils/login-flow');

Page({
  data: {
    loading: false,
    redirect: '',
    form: {
      nickname: '',
      account: '',
      password: '',
      confirmPassword: ''
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
    const { nickname, account, password, confirmPassword } = this.data.form;
    if (nickname.trim().length > 64) {
      return '昵称不能超过 64 个字符';
    }
    if (!account.trim()) {
      return '请填写手机号或邮箱';
    }
    if (!password || password.length < 6 || password.length > 64) {
      return '密码长度需为 6-64 位';
    }
    if (password !== confirmPassword) {
      return '两次输入的密码不一致';
    }
    return '';
  },

  async submitRegister() {
    if (this.data.loading) {
      return;
    }
    const errorMessage = this.validateForm();
    if (errorMessage) {
      wx.showToast({ title: errorMessage, icon: 'none' });
      return;
    }

    const { nickname, account, password } = this.data.form;
    this.setData({ loading: true });
    try {
      const loginResult = await accountRegister({
        nickname: nickname.trim(),
        account: account.trim(),
        password
      });
      wx.showToast({ title: '注册成功', icon: 'success' });
      this.goAfterRegister(loginResult);
    } catch (error) {
      wx.showToast({ title: error.message || '注册失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
    }
  },

  goLogin() {
    const redirect = encodeURIComponent(this.data.redirect || '');
    wx.redirectTo({
      url: `/pages/account-login/index?redirect=${redirect}`
    });
  },

  goAfterRegister(loginResult) {
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
