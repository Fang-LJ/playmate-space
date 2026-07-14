const { createItinerary, getItineraryDetail, updateItinerary } = require('../../services/itinerary');
const { ITINERARY_TYPE } = require('../../utils/p1-display');

const TYPES = Object.keys(ITINERARY_TYPE);

function pad(value) {
  return String(value).padStart(2, '0');
}

function defaultSchedule() {
  const now = new Date();
  const date = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
  const start = new Date(date.getFullYear(), date.getMonth(), date.getDate(), now.getHours(), now.getMinutes());
  start.setMinutes(Math.ceil(start.getMinutes() / 30) * 30, 0, 0);
  const end = new Date(start.getTime() + 60 * 60 * 1000);
  return {
    itineraryDate: `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`,
    startTime: `${pad(start.getHours())}:${pad(start.getMinutes())}`,
    endTime: `${pad(end.getHours())}:${pad(end.getMinutes())}`
  };
}

Page({
  data: {
    activityId: '',
    itineraryId: '',
    saving: false,
    withPoll: false,
    types: TYPES.map((value) => ({ value, label: ITINERARY_TYPE[value] })),
    typeIndex: TYPES.indexOf('OTHER'),
    needsTimeCompletion: false,
    form: {
      title: '',
      itineraryType: 'OTHER',
      ...defaultSchedule(),
      allDay: false,
      locationName: '',
      address: '',
      description: ''
    },
    poll: {
      title: '',
      description: '',
      deadline: '',
      options: [{ optionText: '' }, { optionText: '' }]
    }
  },

  async onLoad(options) {
    this.setData({ activityId: options.activityId || '', itineraryId: options.itineraryId || '' });
    if (!options.itineraryId) return;
    try {
      const detail = await getItineraryDetail(options.activityId, options.itineraryId);
      const itinerary = detail.itinerary || {};
      const historicalAllDay = Boolean(itinerary.allDay);
      const typeIndex = Math.max(0, TYPES.indexOf(itinerary.itineraryType || 'OTHER'));
      this.setData({
        typeIndex,
        needsTimeCompletion: historicalAllDay || !itinerary.startTime || !itinerary.endTime,
        form: {
          ...this.data.form,
          ...itinerary,
          allDay: false,
          startTime: historicalAllDay ? '' : (itinerary.startTime || ''),
          endTime: historicalAllDay ? '' : (itinerary.endTime || '')
        }
      });
    } catch (error) {
      wx.showToast({ title: error.message || '行程加载失败', icon: 'none' });
    }
  },

  input(event) {
    this.setData({ [`form.${event.currentTarget.dataset.key}`]: event.detail.value });
  },

  pickFormValue(event) {
    this.setData({ [`form.${event.currentTarget.dataset.key}`]: event.detail.value });
  },

  pickType(event) {
    const typeIndex = Number(event.detail.value);
    this.setData({ typeIndex, 'form.itineraryType': TYPES[typeIndex] });
  },

  togglePoll() {
    this.setData({ withPoll: !this.data.withPoll });
  },

  pollInput(event) {
    this.setData({ [`poll.${event.currentTarget.dataset.key}`]: event.detail.value });
  },

  optionInput(event) {
    this.setData({ [`poll.options[${event.currentTarget.dataset.index}].optionText`]: event.detail.value });
  },

  addOption() {
    this.setData({ 'poll.options': this.data.poll.options.concat({ optionText: '' }) });
  },

  removeOption(event) {
    const index = Number(event.currentTarget.dataset.index);
    if (this.data.poll.options.length <= 2) return;
    const options = this.data.poll.options.filter((_, itemIndex) => itemIndex !== index);
    this.setData({ 'poll.options': options });
  },

  cancel() {
    wx.navigateBack();
  },

  validateForm() {
    const form = this.data.form;
    if (!form.title.trim()) return '请填写行程标题';
    if (!form.itineraryDate || !form.startTime || !form.endTime) {
      return this.data.needsTimeCompletion ? '历史全天行程请补充开始和结束时间' : '请选择开始和结束时间';
    }
    if (form.endTime <= form.startTime) return '结束时间必须晚于开始时间';
    if (this.data.withPoll) {
      if (!this.data.poll.title.trim()) return '请填写投票问题';
      if (this.data.poll.options.filter((item) => item.optionText.trim()).length < 2) return '请至少填写两个投票选项';
    }
    return '';
  },

  async save() {
    const validationMessage = this.validateForm();
    if (validationMessage) {
      wx.showToast({ title: validationMessage, icon: 'none' });
      return;
    }
    const form = { ...this.data.form, allDay: false };
    this.setData({ saving: true });
    try {
      if (this.data.itineraryId) {
        await updateItinerary(this.data.activityId, this.data.itineraryId, form);
      } else {
        const data = { ...form, creationMode: this.data.withPoll ? 'WITH_POLL' : 'DIRECT' };
        if (this.data.withPoll) {
          data.poll = {
            title: this.data.poll.title.trim(),
            description: this.data.poll.description,
            deadline: this.data.poll.deadline || null,
            purpose: 'UPDATE_ITINERARY',
            decisionType: 'OTHER',
            voteType: 'SINGLE',
            allowModify: true,
            options: this.data.poll.options
              .filter((item) => item.optionText.trim())
              .map((item) => ({ optionText: item.optionText.trim(), resultPayload: {} }))
          };
        }
        await createItinerary(this.data.activityId, data);
      }
      wx.showToast({ title: '已保存', icon: 'success' });
      setTimeout(() => wx.navigateBack(), 400);
    } catch (error) {
      wx.showToast({ title: error.message || '保存失败', icon: 'none' });
    } finally {
      this.setData({ saving: false });
    }
  }
});
