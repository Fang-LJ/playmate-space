const { isLoggedIn } = require('../../services/auth');
const { chooseImage, uploadUserAvatar } = require('../../services/file');
const { getCurrentUser, updateCurrentUserProfile } = require('../../services/user');

Page({
  data: {
    loading: true,
    saving: false,
    uploading: false,
    redirect: '',
    form: {
      nickname: '',
      avatarUrl: ''
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
    this.loadUser();
  },

  async loadUser() {
    try {
      const user = await getCurrentUser();
      this.setData({
        'form.nickname': user.nickname || '',
        'form.avatarUrl': user.avatarUrl || ''
      });
    } catch (error) {
      wx.showToast({ title: error.message || '资料加载失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
    }
  },

  handleNicknameInput(event) {
    const detail = event && event.detail ? event.detail : {};
    const value = detail.value != null ? detail.value : '';
    this.setData({ 'form.nickname': value });
  },

  async uploadAvatar(filePath) {
    if (this.data.uploading) {
      return;
    }
    this.setData({ uploading: true });
    try {
      const result = await uploadUserAvatar(filePath);
      this.setData({ 'form.avatarUrl': result.url });
    } catch (error) {
      wx.showToast({ title: error.message || '头像上传失败', icon: 'none' });
    } finally {
      this.setData({ uploading: false });
    }
  },

  handleChooseAvatar(event) {
    const filePath = event && event.detail && event.detail.avatarUrl;
    if (!filePath) {
      wx.showToast({ title: '未获取到微信头像', icon: 'none' });
      return;
    }
    this.uploadAvatar(filePath);
  },

  async chooseAvatarFromAlbum() {
    try {
      const filePath = await chooseImage();
      await this.uploadAvatar(filePath);
    } catch (error) {
      wx.showToast({ title: error.message || '选择图片失败', icon: 'none' });
    }
  },

  async saveProfile() {
    const nickname = this.data.form.nickname.trim();
    if (!nickname) {
      wx.showToast({ title: '请填写昵称', icon: 'none' });
      return;
    }
    if (nickname.length > 64) {
      wx.showToast({ title: '昵称不能超过 64 个字符', icon: 'none' });
      return;
    }
    this.setData({ saving: true });
    try {
      await updateCurrentUserProfile({ nickname, avatarUrl: this.data.form.avatarUrl });
      wx.showToast({ title: '保存成功', icon: 'success' });
      setTimeout(() => this.goAfterDone(), 450);
    } catch (error) {
      wx.showToast({ title: error.message || '保存失败', icon: 'none' });
    } finally {
      this.setData({ saving: false });
    }
  },

  skip() {
    this.goAfterDone();
  },

  goAfterDone() {
    const redirect = this.data.redirect;
    if (redirect) {
      if (redirect === '/pages/activity-list/index' || redirect === '/pages/mine/index' || redirect === '/pages/book-list/index') {
        wx.switchTab({ url: redirect });
        return;
      }
      wx.redirectTo({ url: redirect });
      return;
    }
    wx.switchTab({ url: '/pages/activity-list/index' });
  }
});
