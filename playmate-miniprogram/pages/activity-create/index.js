const { createActivity } = require('../../services/activity');
const { chooseImage, uploadActivityCover } = require('../../services/file');
const { isLoggedIn } = require('../../services/auth');

const ACTIVITY_TYPES = [
  { label: '旅行', value: 'TRAVEL' },
  { label: '聚餐', value: 'MEAL' },
  { label: '桌游', value: 'BOARD_GAME' },
  { label: '其他', value: 'OTHER' }
];

function getToday() {
  const now = new Date();
  const month = `${now.getMonth() + 1}`.padStart(2, '0');
  const day = `${now.getDate()}`.padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

Page({
  data: {
    submitting: false,
    uploading: false,
    typeOptions: ACTIVITY_TYPES,
    typeIndex: 0,
    currentTypeLabel: ACTIVITY_TYPES[0].label,
    form: {
      name: '',
      type: 'TRAVEL',
      locationName: '',
      address: '',
      startDate: getToday(),
      endDate: getToday(),
      description: '',
      coverFileId: null,
      coverUrl: ''
    }
  },

  onLoad() {
    if (!isLoggedIn()) {
      wx.showToast({
        title: '请先登录',
        icon: 'none'
      });
      setTimeout(() => {
        wx.switchTab({ url: '/pages/mine/index' });
      }, 600);
    }
  },

  handleInput(event) {
    const { field } = event.currentTarget.dataset;
    this.setData({
      [`form.${field}`]: event.detail.value
    });
  },

  handleTypeChange(event) {
    const index = Number(event.detail.value);
    const type = ACTIVITY_TYPES[index] || ACTIVITY_TYPES[0];
    this.setData({
      typeIndex: index,
      currentTypeLabel: type.label,
      'form.type': type.value
    });
  },

  handleTypeTap(event) {
    const { value } = event.currentTarget.dataset;
    const index = Math.max(0, ACTIVITY_TYPES.findIndex((item) => item.value === value));
    const type = ACTIVITY_TYPES[index] || ACTIVITY_TYPES[0];
    this.setData({
      typeIndex: index,
      currentTypeLabel: type.label,
      'form.type': type.value
    });
  },

  handleDateChange(event) {
    const { field } = event.currentTarget.dataset;
    this.setData({
      [`form.${field}`]: event.detail.value
    });
  },

  async chooseCover() {
    if (this.data.uploading) {
      return;
    }
    if (!isLoggedIn()) {
      wx.showToast({
        title: '请先登录',
        icon: 'none'
      });
      return;
    }

    this.setData({ uploading: true });
    try {
      const filePath = await chooseImage();
      const result = await uploadActivityCover(filePath);
      this.setData({
        'form.coverFileId': result.fileId,
        'form.coverUrl': result.url
      });
      wx.showToast({
        title: '封面已上传',
        icon: 'success'
      });
    } catch (error) {
      wx.showToast({
        title: error.message || '封面上传失败',
        icon: 'none'
      });
    } finally {
      this.setData({ uploading: false });
    }
  },

  validateForm() {
    const form = this.data.form;
    if (!form.name.trim()) {
      return '请填写活动名称';
    }
    if (!form.type) {
      return '请选择活动类型';
    }
    if (!form.startDate || !form.endDate) {
      return '请选择活动日期';
    }
    if (form.startDate > form.endDate) {
      return '开始日期不能晚于结束日期';
    }
    return '';
  },

  async submitActivity() {
    if (this.data.submitting) {
      return;
    }
    const errorMessage = this.validateForm();
    if (errorMessage) {
      wx.showToast({
        title: errorMessage,
        icon: 'none'
      });
      return;
    }

    const form = this.data.form;
    const payload = {
      name: form.name.trim(),
      type: form.type,
      coverFileId: form.coverFileId,
      locationName: form.locationName.trim(),
      address: form.address.trim(),
      startDate: form.startDate,
      endDate: form.endDate,
      description: form.description.trim()
    };

    this.setData({ submitting: true });
    try {
      await createActivity(payload);
      wx.showToast({
        title: '创建成功',
        icon: 'success'
      });
      setTimeout(() => {
        wx.navigateBack();
      }, 500);
    } catch (error) {
      wx.showToast({
        title: error.message || '创建失败',
        icon: 'none'
      });
    } finally {
      this.setData({ submitting: false });
    }
  },

  cancelBack() {
    wx.navigateBack();
  }
});
