const { isLoggedIn } = require('../../services/auth');
const { chooseImage, uploadUserAvatar } = require('../../services/file');
const { getCurrentUser, updateCurrentUserProfile } = require('../../services/user');
const { clearWechatProfileSuggestion } = require('../../utils/login-flow');

Page({
  data: {
    loading: true,
    saving: false,
    uploading: false,
    form: {
      nickname: '',
      avatarUrl: ''
    }
  },

  onLoad() {
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
    this.setData({ 'form.nickname': event.detail.value });
  },

  async chooseAvatar() {
    if (this.data.uploading) {
      return;
    }
    this.setData({ uploading: true });
    try {
      const filePath = await chooseImage();
      const result = await uploadUserAvatar(filePath);
      this.setData({ 'form.avatarUrl': result.url });
    } catch (error) {
      wx.showToast({ title: error.message || '头像上传失败', icon: 'none' });
    } finally {
      this.setData({ uploading: false });
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
      clearWechatProfileSuggestion();
      wx.showToast({ title: '保存成功', icon: 'success' });
      setTimeout(() => wx.navigateBack(), 450);
    } catch (error) {
      wx.showToast({ title: error.message || '保存失败', icon: 'none' });
    } finally {
      this.setData({ saving: false });
    }
  },

  skip() {
    clearWechatProfileSuggestion();
    wx.navigateBack();
  }
});
