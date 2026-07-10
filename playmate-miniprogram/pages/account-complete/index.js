const { updateMyAccount, getCurrentUser } = require('../../services/user');
const { isLoggedIn } = require('../../services/auth');

Page({
  data: {
    loading: false,
    redirect: '',
    user: null,
    form: {
      phone: '',
      email: '',
      password: '',
      confirmPassword: ''
    }
  },

  onLoad(options) {
    this.setData({
      redirect: options.redirect ? decodeURIComponent(options.redirect) : ''
    });
    if (!isLoggedIn()) {
      wx.redirectTo({ url: '/pages/login/index' });
      return;
    }
    this.loadCurrentUser();
  },

  async loadCurrentUser() {
    try {
      const user = await getCurrentUser();
      this.setData({
        user,
        'form.phone': user.phone || '',
        'form.email': user.email || ''
      });
    } catch (error) {
      wx.showToast({ title: error.message || '账号信息加载失败', icon: 'none' });
    }
  },

  handleInput(event) {
    const { field } = event.currentTarget.dataset;
    this.setData({
      [`form.${field}`]: event.detail.value
    });
  },

  validateForm() {
    const { phone, email, password, confirmPassword } = this.data.form;
    if (!phone.trim() && !email.trim()) {
      return '请至少填写手机号或邮箱';
    }
    if (phone.trim().length > 32) {
      return '手机号不能超过 32 个字符';
    }
    if (email.trim().length > 128) {
      return '邮箱不能超过 128 个字符';
    }
    if (!password || password.length < 6 || password.length > 64) {
      return '密码长度需为 6-64 位';
    }
    if (password !== confirmPassword) {
      return '两次输入的密码不一致';
    }
    return '';
  },

  async submitAccount() {
    if (this.data.loading) {
      return;
    }
    const errorMessage = this.validateForm();
    if (errorMessage) {
      wx.showToast({ title: errorMessage, icon: 'none' });
      return;
    }

    const { phone, email, password } = this.data.form;
    const payload = {};
    if (phone.trim()) {
      payload.phone = phone.trim();
    }
    if (email.trim()) {
      payload.email = email.trim();
    }
    if (password) {
      payload.password = password;
    }

    this.setData({ loading: true });
    try {
      await updateMyAccount(payload);
      wx.showToast({ title: '账号保护已设置', icon: 'success' });
      setTimeout(() => this.goRedirectTarget(), 500);
    } catch (error) {
      wx.showToast({ title: error.message || '保存失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
    }
  },

  skipComplete() {
    wx.showModal({
      title: '暂时跳过',
      content: '跳过后仍可在我的页继续设置账号保护。',
      confirmText: '跳过',
      success: (res) => {
        if (res.confirm) {
          this.goRedirectTarget();
        }
      }
    });
  },

  goRedirectTarget() {
    const redirect = this.data.redirect;
    if (!redirect) {
      const pages = getCurrentPages();
      if (pages.length > 1) {
        wx.navigateBack();
        return;
      }
      wx.switchTab({ url: '/pages/mine/index' });
      return;
    }
    if (redirect === '/pages/activity-list/index' || redirect === '/pages/mine/index') {
      wx.switchTab({ url: redirect });
      return;
    }
    wx.redirectTo({ url: redirect });
  }
});
