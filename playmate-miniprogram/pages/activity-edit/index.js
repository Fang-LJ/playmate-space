const { getActivityDetail, updateActivity } = require('../../services/activity');
const { chooseImage, uploadActivityCover } = require('../../services/file');

const ACTIVITY_TYPES = [
  { label: '旅行', value: 'TRAVEL' },
  { label: '聚餐', value: 'MEAL' },
  { label: '桌游', value: 'BOARD_GAME' },
  { label: '其他', value: 'OTHER' }
];

Page({
  data: {
    activityId: '',
    loading: true,
    submitting: false,
    uploading: false,
    isEnded: false,
    isCanceled: false,
    typeOptions: ACTIVITY_TYPES,
    typeIndex: 0,
    currentTypeLabel: ACTIVITY_TYPES[0].label,
    form: {
      name: '',
      type: 'TRAVEL',
      locationName: '',
      address: '',
      startDate: '',
      endDate: '',
      description: '',
      coverFileId: null,
      coverUrl: ''
    },
    errorMessage: ''
  },

  onLoad(options) {
    const activityId = options.activityId;
    this.setData({ activityId });
    this.loadActivity(activityId);
  },

  async loadActivity(activityId) {
    if (!activityId) {
      this.setData({
        loading: false,
        errorMessage: '缺少活动 ID'
      });
      return;
    }

    this.setData({ loading: true, errorMessage: '' });
    try {
      const activity = await getActivityDetail(activityId);
      const typeIndex = Math.max(0, ACTIVITY_TYPES.findIndex((item) => item.value === activity.type));
      const type = ACTIVITY_TYPES[typeIndex] || ACTIVITY_TYPES[0];
      this.setData({
        isEnded: activity.status === 'ENDED',
        isCanceled: activity.status === 'CANCELED',
        typeIndex,
        currentTypeLabel: type.label,
        form: {
          name: activity.name || '',
          type: activity.type || type.value,
          locationName: activity.locationName || '',
          address: activity.address || '',
          startDate: activity.startDate || '',
          endDate: activity.endDate || '',
          description: activity.description || '',
          coverFileId: activity.coverFileId || null,
          coverUrl: activity.coverUrl || ''
        }
      });
    } catch (error) {
      this.setData({
        errorMessage: error.message || '活动加载失败'
      });
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
    if (this.data.isCanceled) {
      wx.showToast({ title: '已取消活动不可编辑', icon: 'none' });
      return;
    }
    if (this.data.uploading) {
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
      wx.showToast({ title: '封面已上传', icon: 'success' });
    } catch (error) {
      wx.showToast({ title: error.message || '封面上传失败', icon: 'none' });
    } finally {
      this.setData({ uploading: false });
    }
  },

  validateForm() {
    const form = this.data.form;
    if (this.data.isEnded) {
      return '';
    }
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

  buildPayload() {
    const form = this.data.form;
    if (this.data.isEnded) {
      return {
        coverFileId: form.coverFileId,
        description: form.description.trim()
      };
    }
    return {
      name: form.name.trim(),
      type: form.type,
      coverFileId: form.coverFileId,
      locationName: form.locationName.trim(),
      address: form.address.trim(),
      startDate: form.startDate,
      endDate: form.endDate,
      description: form.description.trim()
    };
  },

  async submitEdit() {
    if (this.data.isCanceled) {
      wx.showToast({ title: '已取消活动不可编辑', icon: 'none' });
      return;
    }
    if (this.data.submitting) {
      return;
    }
    const errorMessage = this.validateForm();
    if (errorMessage) {
      wx.showToast({ title: errorMessage, icon: 'none' });
      return;
    }

    this.setData({ submitting: true });
    try {
      await updateActivity(this.data.activityId, this.buildPayload());
      wx.showToast({ title: '已保存', icon: 'success' });
      setTimeout(() => {
        wx.navigateBack();
      }, 500);
    } catch (error) {
      wx.showToast({ title: error.message || '保存失败', icon: 'none' });
    } finally {
      this.setData({ submitting: false });
    }
  },

  retryLoad() {
    this.loadActivity(this.data.activityId);
  },

  cancelBack() {
    wx.navigateBack();
  }
});
