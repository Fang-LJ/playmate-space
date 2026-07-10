const { isLoggedIn } = require('../../services/auth');
const { chooseImage, uploadUserAvatar } = require('../../services/file');
const { getCurrentUser, updateCurrentUserProfile } = require('../../services/user');

Page({
  data: {
    loading: true,
    uploading: false,
    submitting: false,
    form: {
      nickname: '',
      avatarUrl: '',
      phone: '',
      email: '',
      gender: 'UNKNOWN',
      address: '',
      bio: ''
    },
    genderOptions: [
      { label: '保密', value: 'UNKNOWN' },
      { label: '男', value: 'MALE' },
      { label: '女', value: 'FEMALE' },
      { label: '其他', value: 'OTHER' }
    ]
  },

  onLoad() {
    if (!isLoggedIn()) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      setTimeout(() => {
        wx.navigateTo({ url: '/pages/login/index' });
      }, 500);
      return;
    }
    this.loadProfile();
  },

  async loadProfile() {
    this.setData({ loading: true });
    try {
      const user = await getCurrentUser();
      this.setData({
        form: {
          nickname: user.nickname || '',
          avatarUrl: user.avatarUrl || '',
          phone: user.phone || '',
          email: user.email || '',
          gender: user.gender || 'UNKNOWN',
          address: user.address || '',
          bio: user.bio || ''
        }
      });
    } catch (error) {
      wx.showToast({ title: error.message || '资料加载失败', icon: 'none' });
      if (error.code === 'UNAUTHORIZED') {
        setTimeout(() => {
          wx.navigateTo({ url: '/pages/login/index' });
        }, 500);
      }
    } finally {
      this.setData({ loading: false });
    }
  },

  handleInput(event) {
    const { field } = event.currentTarget.dataset;
    this.setData({
      [`form.${field}`]: event.detail.value
    });
  },

  selectGender(event) {
    const { value } = event.currentTarget.dataset;
    this.setData({
      'form.gender': value
    });
  },

  async chooseAvatar() {
    if (this.data.uploading) {
      return;
    }
    this.setData({ uploading: true });
    try {
      const filePath = await chooseImage();
      const result = await uploadUserAvatar(filePath);
      this.setData({
        'form.avatarUrl': result.url
      });
      wx.showToast({ title: '头像已上传', icon: 'success' });
    } catch (error) {
      wx.showToast({ title: error.message || '头像上传失败', icon: 'none' });
    } finally {
      this.setData({ uploading: false });
    }
  },

  validateForm() {
    const { nickname, phone, email, address, bio } = this.data.form;
    if (!nickname.trim()) {
      return '请填写昵称';
    }
    if (nickname.trim().length > 64) {
      return '昵称不能超过 64 个字符';
    }
    if (phone.trim().length > 32) {
      return '联系电话不能超过 32 个字符';
    }
    if (email.trim().length > 128) {
      return '邮箱不能超过 128 个字符';
    }
    if (address.trim().length > 255) {
      return '地址不能超过 255 个字符';
    }
    if (bio.trim().length > 512) {
      return '个人简介不能超过 512 个字符';
    }
    return '';
  },

  async submitProfile() {
    if (this.data.submitting || this.data.uploading) {
      return;
    }
    const errorMessage = this.validateForm();
    if (errorMessage) {
      wx.showToast({ title: errorMessage, icon: 'none' });
      return;
    }

    const { nickname, avatarUrl, phone, email, gender, address, bio } = this.data.form;
    this.setData({ submitting: true });
    try {
      await updateCurrentUserProfile({
        nickname: nickname.trim(),
        avatarUrl: avatarUrl.trim(),
        phone: phone.trim(),
        email: email.trim(),
        gender,
        address: address.trim(),
        bio: bio.trim()
      });
      wx.showToast({ title: '保存成功', icon: 'success' });
      setTimeout(() => {
        wx.navigateBack();
      }, 500);
    } catch (error) {
      wx.showToast({ title: error.message || '保存失败', icon: 'none' });
    } finally {
      this.setData({ submitting: false });
    }
  },

  cancelBack() {
    wx.navigateBack();
  }
});
