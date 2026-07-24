const { createPoll } = require('../../services/poll');
const { getItineraryDetail } = require('../../services/itinerary');
const { DECISION_SCOPE, FIELD_LABEL, POLL_DECISION, POLL_PURPOSE, itinerarySummary } = require('../../utils/p1-display');

const PURPOSES = ['GENERAL', 'UPDATE_ITINERARY', 'CREATE_ITINERARY'];
const DECISIONS = Object.keys(POLL_DECISION);

function localDate() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}

function emptyOption() {
  return {
    optionText: '',
    optionDescription: '',
    transportMode: '',
    departureName: '',
    destinationName: '',
    routeDetail: '',
    itineraryDate: '',
    startTime: '',
    endTime: '',
    mealType: '',
    restaurantName: '',
    address: '',
    activityContent: '',
    locationName: '',
    title: ''
  };
}

function defaultDecisionForType(type) {
  if (type === 'TRANSPORT') return 'TRANSPORT';
  if (type === 'MEAL') return 'RESTAURANT';
  if (['ACTIVITY', 'SIGHTSEEING'].includes(type)) return 'CONTENT';
  return 'PLACE';
}

Page({
  data: {
    activityId: '',
    saving: false,
    target: null,
    purposeOptions: PURPOSES.map((value) => ({ value, label: POLL_PURPOSE[value] })),
    decisionOptions: DECISIONS.map((value) => ({ value, label: POLL_DECISION[value] })),
    purposeIndex: 0,
    decisionIndex: DECISIONS.indexOf('OTHER'),
    scopeLabels: [],
    unchangedLabels: [],
    unchangedText: '',
    template: { title: '', itineraryDate: localDate(), startTime: '', endTime: '', itineraryType: 'OTHER' },
    form: {
      title: '',
      description: '',
      purpose: 'GENERAL',
      decisionType: 'OTHER',
      targetItineraryId: null,
      voteType: 'SINGLE',
      allowModify: true,
      deadlineDate: '',
      deadlineTime: '',
      options: [emptyOption(), emptyOption()]
    }
  },

  async onLoad(options) {
    const purpose = options.purpose || 'GENERAL';
    const targetItineraryId = options.targetItineraryId ? Number(options.targetItineraryId) : null;
    this.setData({
      activityId: options.activityId || '',
      purposeIndex: Math.max(0, PURPOSES.indexOf(purpose)),
      'form.purpose': purpose,
      'form.targetItineraryId': targetItineraryId
    });
    if (!targetItineraryId) {
      this.updateDecisionMeta();
      return;
    }
    try {
      const detail = await getItineraryDetail(options.activityId, targetItineraryId);
      const itinerary = detail.itinerary || {};
      const decisionType = defaultDecisionForType(itinerary.itineraryType);
      this.setData({
        target: { ...itinerary, summaryText: itinerarySummary(itinerary) },
        decisionIndex: DECISIONS.indexOf(decisionType),
        'form.decisionType': decisionType,
        'form.title': `确定${itinerary.title || '这个行程'}的${POLL_DECISION[decisionType]}`,
        'template.title': itinerary.title || '',
        'template.itineraryDate': itinerary.itineraryDate || localDate(),
        'template.startTime': itinerary.startTime || '',
        'template.endTime': itinerary.endTime || '',
        'template.itineraryType': itinerary.itineraryType || 'OTHER'
      });
      this.updateDecisionMeta();
    } catch (error) {
      wx.showToast({ title: error.message || '关联行程加载失败', icon: 'none' });
    }
  },

  input(event) {
    this.setData({ [`form.${event.currentTarget.dataset.key}`]: event.detail.value });
  },

  choosePurpose(event) {
    const purposeIndex = Number(event.detail.value);
    const purpose = PURPOSES[purposeIndex];
    this.setData({
      purposeIndex,
      'form.purpose': purpose,
      'form.voteType': purpose === 'GENERAL' ? this.data.form.voteType : 'SINGLE'
    });
    this.updateDecisionMeta();
  },

  chooseDecision(event) {
    const decisionIndex = Number(event.detail.value);
    this.setData({ decisionIndex, 'form.decisionType': DECISIONS[decisionIndex] });
    this.updateDecisionMeta();
  },

  updateDecisionMeta() {
    const scope = this.data.form.purpose === 'GENERAL'
      ? []
      : (DECISION_SCOPE[this.data.form.decisionType] || []);
    const allFields = Object.keys(FIELD_LABEL);
    this.setData({
      scopeLabels: scope.map((field) => FIELD_LABEL[field]),
      unchangedLabels: allFields.filter((field) => !scope.includes(field)).map((field) => FIELD_LABEL[field]),
      unchangedText: allFields.filter((field) => !scope.includes(field)).map((field) => FIELD_LABEL[field]).join('、')
    });
  },

  chooseVoteType(event) {
    this.setData({ 'form.voteType': event.currentTarget.dataset.value });
  },

  toggleAllowModify(event) {
    this.setData({ 'form.allowModify': event.detail.value });
  },

  deadlineInput(event) {
    this.setData({ [`form.${event.currentTarget.dataset.key}`]: event.detail.value });
  },

  templateInput(event) {
    this.setData({ [`template.${event.currentTarget.dataset.key}`]: event.detail.value });
  },

  optionInput(event) {
    const { index, key } = event.currentTarget.dataset;
    this.setData({ [`form.options[${index}].${key}`]: event.detail.value });
  },

  addOption() {
    this.setData({ 'form.options': this.data.form.options.concat(emptyOption()) });
  },

  removeOption(event) {
    const index = Number(event.currentTarget.dataset.index);
    if (this.data.form.options.length <= 2) return;
    this.setData({ 'form.options': this.data.form.options.filter((_, itemIndex) => itemIndex !== index) });
  },

  buildDeadline() {
    const { deadlineDate, deadlineTime } = this.data.form;
    if (!deadlineDate && !deadlineTime) return null;
    if (!deadlineDate || !deadlineTime) return '';
    return `${deadlineDate}T${deadlineTime}:00`;
  },

  validate() {
    const { form, template } = this.data;
    if (!form.title.trim()) return '请填写投票问题';
    const options = form.options.filter((item) => item.optionText.trim());
    if (options.length < 2) return '请至少填写两个投票选项';
    const deadline = this.buildDeadline();
    if (deadline === '') return '请同时选择截止日期和时间';
    if (form.purpose === 'CREATE_ITINERARY') {
      if (!template.title.trim() || !template.itineraryDate || !template.startTime || !template.endTime) {
        return '请填写生成行程的标题、日期和时间';
      }
      if (template.itineraryType !== 'LODGING' && template.endTime <= template.startTime) {
        return '生成行程的结束时间必须晚于开始时间';
      }
    }
    if (form.purpose !== 'GENERAL') {
      const scope = DECISION_SCOPE[form.decisionType] || [];
      const missingPayload = options.some((item) => !scope.some((field) => String(item[field] || '').trim()));
      if (missingPayload) return '请为每个选项填写本次要决定的内容';
    }
    return '';
  },

  buildPayload(option) {
    const scope = DECISION_SCOPE[this.data.form.decisionType] || [];
    return scope.reduce((payload, field) => {
      const value = String(option[field] || '').trim();
      if (value) payload[field] = value;
      return payload;
    }, {});
  },

  async save() {
    const message = this.validate();
    if (message) return wx.showToast({ title: message, icon: 'none' });
    const { form, template } = this.data;
    const options = form.options.filter((item) => item.optionText.trim()).map((item) => ({
      optionText: item.optionText.trim(),
      optionDescription: item.optionDescription.trim(),
      resultPayload: form.purpose === 'GENERAL' ? {} : this.buildPayload(item)
    }));
    const itineraryTemplate = form.purpose === 'CREATE_ITINERARY'
      ? { ...template, allDay: false }
      : {};
    this.setData({ saving: true });
    try {
      const response = await createPoll(this.data.activityId, {
        title: form.title.trim(),
        description: form.description.trim(),
        purpose: form.purpose,
        decisionType: form.decisionType,
        decisionScope: form.purpose === 'GENERAL' ? [] : DECISION_SCOPE[form.decisionType],
        targetItineraryId: form.targetItineraryId,
        voteType: form.voteType,
        allowModify: form.allowModify,
        deadline: this.buildDeadline(),
        itineraryTemplate,
        options
      });
      wx.showToast({ title: '投票已创建', icon: 'success' });
      setTimeout(() => wx.redirectTo({
        url: `/pages/poll-detail/index?activityId=${this.data.activityId}&pollId=${response.pollId}`
      }), 400);
    } catch (error) {
      wx.showToast({ title: error.message || '创建失败', icon: 'none' });
    } finally {
      this.setData({ saving: false });
    }
  }
});
