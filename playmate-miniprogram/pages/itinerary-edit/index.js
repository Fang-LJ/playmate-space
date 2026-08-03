const {
  createItinerary,
  getItineraryDetail,
  getItineraryTypeMetadata,
  updateItinerary
} = require('../../services/itinerary');
const { ITINERARY_STATUS, label } = require('../../utils/p1-display');
const {
  buildFormViewModel,
  normalizeMetadata,
  typeOptions,
  typeSpecificKeys
} = require('../../utils/itinerary-ui');

const TYPE_COPY = {
  TRANSPORT: '交通行程重点是方式、起点、终点和路线。',
  MEAL: '用餐行程重点是吃什么、去哪家店和具体地址。',
  LODGING: '住宿只保留酒店、地址和入住安排，避免表单过重。',
  SIGHTSEEING: '景点行程重点是游玩内容、景点名称和地址。',
  ACTIVITY: '活动行程重点是活动内容、地点和具体安排。',
  OTHER: '其他行程保留地点和备注，适合自由安排。'
};

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

function createForm(metadata, itinerary) {
  const form = {
    title: '',
    itineraryType: 'OTHER',
    ...defaultSchedule(),
    allDay: false,
    description: ''
  };
  normalizeMetadata(metadata).forEach((definition) => {
    [...(definition.focusFields || []), ...(definition.commonFields || [])].forEach((field) => {
      if (!(field.key in form)) form[field.key] = '';
    });
  });
  if (!itinerary) return form;
  Object.keys(form).forEach((key) => {
    if (itinerary[key] !== undefined && itinerary[key] !== null) form[key] = itinerary[key];
  });
  form.itineraryType = itinerary.itineraryType || 'OTHER';
  form.allDay = false;
  return form;
}

Page({
  data: {
    activityId: '',
    itineraryId: '',
    loading: true,
    metadataError: '',
    saving: false,
    withPoll: false,
    metadata: [],
    types: [],
    form: null,
    formView: null,
    typeCopy: '',
    statusText: '保存后已确认',
    needsTimeCompletion: false,
    poll: {
      title: '',
      description: '',
      deadline: '',
      options: [{ optionText: '' }, { optionText: '' }]
    }
  },

  onLoad(options) {
    this.setData({ activityId: options.activityId || '', itineraryId: options.itineraryId || '' });
    this.load();
  },

  async load(forceMetadata = false) {
    this.setData({ loading: true, metadataError: '' });
    try {
      const [metadataResponse, detail] = await Promise.all([
        getItineraryTypeMetadata(forceMetadata),
        this.data.itineraryId
          ? getItineraryDetail(this.data.activityId, this.data.itineraryId)
          : Promise.resolve(null)
      ]);
      const metadata = normalizeMetadata(metadataResponse);
      if (metadata.length !== 6) throw new Error('行程类型信息不完整，请重试');
      const itinerary = detail && detail.itinerary;
      const historicalAllDay = Boolean(itinerary && itinerary.allDay);
      const form = createForm(metadata, itinerary);
      if (historicalAllDay) {
        form.startTime = '';
        form.endTime = '';
      }
      this.setData({
        metadata,
        types: typeOptions(metadata),
        form,
        formView: buildFormViewModel(metadata, form.itineraryType, form),
        typeCopy: TYPE_COPY[form.itineraryType] || '',
        statusText: itinerary
          ? label(ITINERARY_STATUS, itinerary.planningStatus)
          : '保存后已确认',
        needsTimeCompletion: historicalAllDay || Boolean(itinerary && (!itinerary.startTime || !itinerary.endTime))
      });
    } catch (error) {
      this.setData({ metadataError: error.message || '行程类型信息加载失败' });
    } finally {
      this.setData({ loading: false });
    }
  },

  retryMetadata() {
    this.load(true);
  },

  applyFormValue(key, value) {
    const form = { ...this.data.form, [key]: value };
    this.setData({
      form,
      formView: buildFormViewModel(this.data.metadata, form.itineraryType, form)
    });
  },

  input(event) {
    this.applyFormValue(event.currentTarget.dataset.key, event.detail.value);
  },

  pickFormValue(event) {
    this.applyFormValue(event.currentTarget.dataset.key, event.detail.value);
  },

  pickType(event) {
    const nextType = event.currentTarget.dataset.type;
    if (!nextType || nextType === this.data.form.itineraryType) return;
    const form = { ...this.data.form, itineraryType: nextType };
    typeSpecificKeys(this.data.metadata).forEach((key) => {
      form[key] = '';
    });
    this.setData({
      form,
      formView: buildFormViewModel(this.data.metadata, nextType, form),
      typeCopy: TYPE_COPY[nextType] || ''
    });
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
    this.setData({ 'poll.options': this.data.poll.options.filter((_, itemIndex) => itemIndex !== index) });
  },

  cancel() {
    wx.navigateBack();
  },

  validateForm() {
    const form = this.data.form;
    if (!form || !form.title.trim()) return '请填写行程标题';
    if (!form.itineraryDate || !form.startTime || !form.endTime) {
      return this.data.needsTimeCompletion ? '历史全天行程请补充开始和结束时间' : '请选择开始和结束时间';
    }
    if (form.itineraryType !== 'LODGING' && form.endTime <= form.startTime) {
      return '结束时间必须晚于开始时间';
    }
    if (this.data.withPoll) {
      if (!this.data.poll.title.trim()) return '请填写投票问题';
      if (this.data.poll.options.filter((item) => item.optionText.trim()).length < 2) return '请至少填写两个投票选项';
    }
    return '';
  },

  async save() {
    if (this.data.saving) return;
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
          const pollConfig = this.linkedPollConfig(form.itineraryType);
          data.poll = {
            title: this.data.poll.title.trim(),
            description: this.data.poll.description,
            deadline: this.data.poll.deadline || null,
            purpose: 'UPDATE_ITINERARY',
            decisionType: pollConfig.decisionType,
            decisionScope: pollConfig.decisionScope,
            voteType: 'SINGLE',
            allowModify: true,
            options: this.data.poll.options
              .filter((item) => item.optionText.trim())
              .map((item) => ({
                optionText: item.optionText.trim(),
                resultPayload: { [pollConfig.payloadField]: item.optionText.trim() }
              }))
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
  },

  linkedPollConfig(itineraryType) {
    if (itineraryType === 'TRANSPORT') {
      return { decisionType: 'TRANSPORT', decisionScope: ['transportMode'], payloadField: 'transportMode' };
    }
    if (itineraryType === 'MEAL') {
      return { decisionType: 'RESTAURANT', decisionScope: ['restaurantName'], payloadField: 'restaurantName' };
    }
    if (['ACTIVITY', 'SIGHTSEEING'].includes(itineraryType)) {
      return { decisionType: 'CONTENT', decisionScope: ['activityContent'], payloadField: 'activityContent' };
    }
    return { decisionType: 'PLACE', decisionScope: ['locationName'], payloadField: 'locationName' };
  }
});
