const { createPoll } = require('../../services/poll');
const { getItineraries, getItineraryDetail, getItineraryTypeMetadata } = require('../../services/itinerary');
const { POLL_DECISION, itinerarySummary } = require('../../utils/p1-display');

const GENERAL_DECISIONS = Object.keys(POLL_DECISION).filter((value) => value !== 'FULL_PLAN');
const BASE_PLAN_FIELDS = ['title', 'itineraryDate', 'startTime', 'endTime'];
const PLAN_NUMBER_TEXT = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十'];

function emptyOption() {
  return { optionText: '', optionDescription: '' };
}

function uniqueFields(fields) {
  const seen = new Set();
  return fields.filter((field) => {
    if (!field || !field.key || seen.has(field.key)) return false;
    seen.add(field.key);
    return true;
  });
}

function buildFullPlanFields(metadata, itineraryType) {
  const definition = (Array.isArray(metadata) ? metadata : [])
    .find((item) => item.type === itineraryType);
  if (!definition) return [];
  const fields = uniqueFields([...(definition.focusFields || []), ...(definition.commonFields || [])]);
  const byKey = fields.reduce((result, field) => {
    result[field.key] = field;
    return result;
  }, {});
  const ordered = [
    ...BASE_PLAN_FIELDS.map((key) => byKey[key]).filter(Boolean),
    ...fields.filter((field) => !BASE_PLAN_FIELDS.includes(field.key) && field.key !== 'description'),
    byKey.description
  ].filter(Boolean);
  return uniqueFields(ordered).map((field) => ({
    ...field,
    control: field.key === 'itineraryDate'
      ? 'date' : (['startTime', 'endTime'].includes(field.key)
        ? 'time' : (field.key === 'description' ? 'textarea' : 'input')),
    layoutClass: ['itineraryDate', 'startTime', 'endTime'].includes(field.key) ? 'half' : 'full'
  }));
}

function fieldValue(value) {
  if (value === null || value === undefined) return '';
  return String(value).replace(/^(\d{2}:\d{2})(?::\d{2})$/, '$1');
}

function copyCurrentPlan(target, scope, index) {
  const option = { optionText: planLabel(index), optionDescription: '' };
  scope.forEach((key) => {
    option[key] = fieldValue(target[key]);
  });
  return option;
}

function copyOriginalPlan(target, scope) {
  const option = copyCurrentPlan(target, scope, 0);
  option.optionText = '原方案';
  return option;
}

function planLabel(index) {
  const number = index + 1;
  return `方案${PLAN_NUMBER_TEXT[index] || number}`;
}

function pad(value) {
  return String(value).padStart(2, '0');
}

function defaultDeadline() {
  const date = new Date(Date.now() + 30 * 60 * 1000);
  return {
    deadlineDate: `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`,
    deadlineTime: `${pad(date.getHours())}:${pad(date.getMinutes())}`
  };
}

function itineraryOption(itinerary) {
  const summary = itinerarySummary(itinerary);
  return {
    itineraryId: Number(itinerary.itineraryId),
    label: itinerary.title || '未命名行程',
    description: [itinerary.itineraryDate, summary].filter(Boolean).join(' · ')
  };
}

Page({
  data: {
    activityId: '',
    saving: false,
    loadingTarget: false,
    loadError: '',
    isFullPlan: false,
    isGeneral: false,
    allowTargetSelection: false,
    targetOptions: [],
    targetIndex: 0,
    targetSelected: false,
    selectedTargetOption: null,
    typeMetadata: [],
    target: null,
    targetTypeLabel: '',
    fullPlanFields: [],
    fullPlanScope: [],
    planBaseFields: [],
    planArrangementFields: [],
    planDescriptionField: null,
    planEditorVisible: false,
    planEditorIndex: -1,
    planEditorTitle: '',
    planDraft: null,
    decisionOptions: GENERAL_DECISIONS.map((value) => ({ value, label: POLL_DECISION[value] })),
    decisionIndex: Math.max(0, GENERAL_DECISIONS.indexOf('OTHER')),
    form: {
      title: '', description: '', purpose: 'GENERAL', decisionType: 'OTHER',
      targetItineraryId: null, voteType: 'SINGLE', allowModify: true,
      deadlineDate: '', deadlineTime: '', options: [emptyOption(), emptyOption()]
    }
  },

  onLoad(options) {
    const targetItineraryId = options.targetItineraryId ? Number(options.targetItineraryId) : null;
    const isGeneral = options.mode === 'general';
    const deadline = defaultDeadline();
    this.setData({
      activityId: options.activityId || '',
      isFullPlan: Boolean(targetItineraryId),
      isGeneral,
      allowTargetSelection: !targetItineraryId && !isGeneral,
      'form.purpose': targetItineraryId || !isGeneral ? 'UPDATE_ITINERARY' : 'GENERAL',
      'form.decisionType': targetItineraryId || !isGeneral ? 'FULL_PLAN' : 'OTHER',
      'form.targetItineraryId': targetItineraryId,
      'form.voteType': 'SINGLE',
      'form.deadlineDate': deadline.deadlineDate,
      'form.deadlineTime': deadline.deadlineTime
    });
    if (targetItineraryId) {
      this.loadTarget(targetItineraryId);
    } else if (!isGeneral) {
      this.loadItineraryOptions();
    }
  },

  async loadItineraryOptions() {
    this.setData({ loadingTarget: true, loadError: '' });
    try {
      const [itineraries, metadata] = await Promise.all([
        getItineraries(this.data.activityId),
        getItineraryTypeMetadata()
      ]);
      const targetOptions = (itineraries || []).map(itineraryOption);
      this.setData({ targetOptions, typeMetadata: metadata || [] });
    } catch (error) {
      const message = error.message || '行程列表加载失败';
      this.setData({ loadError: message });
      wx.showToast({ title: message, icon: 'none' });
    } finally {
      this.setData({ loadingTarget: false });
    }
  },

  chooseTarget(event) {
    const targetIndex = Number(event.detail.value);
    const selected = this.data.targetOptions[targetIndex];
    if (!selected) return;
    this.setData({
      targetIndex,
      targetSelected: true,
      selectedTargetOption: selected,
      isFullPlan: true,
      target: null,
      'form.purpose': 'UPDATE_ITINERARY',
      'form.decisionType': 'FULL_PLAN',
      'form.targetItineraryId': selected.itineraryId,
      'form.voteType': 'SINGLE'
    });
    this.loadTarget(selected.itineraryId, this.data.typeMetadata);
  },

  async loadTarget(targetItineraryId, cachedMetadata) {
    this.setData({ loadingTarget: true, loadError: '' });
    try {
      const [detail, metadata] = await Promise.all([
        getItineraryDetail(this.data.activityId, targetItineraryId),
        cachedMetadata && cachedMetadata.length ? cachedMetadata : getItineraryTypeMetadata()
      ]);
      const itinerary = detail.itinerary || {};
      const fields = buildFullPlanFields(metadata, itinerary.itineraryType);
      const scope = fields.map((field) => field.key);
      const planBaseFields = fields.filter((field) => BASE_PLAN_FIELDS.includes(field.key));
      const planArrangementFields = fields.filter((field) => !BASE_PLAN_FIELDS.includes(field.key) && field.key !== 'description');
      const planDescriptionField = fields.find((field) => field.key === 'description') || null;
      if (!fields.length || !scope.includes('title') || !scope.includes('itineraryDate')) {
        throw new Error('行程类型元数据不完整，请稍后重试');
      }
      const typeDefinition = metadata.find((item) => item.type === itinerary.itineraryType);
      this.setData({
        target: { ...itinerary, summaryText: itinerarySummary(itinerary) },
        isFullPlan: true,
        typeMetadata: metadata,
        targetTypeLabel: typeDefinition ? typeDefinition.label : '',
        fullPlanFields: fields,
        fullPlanScope: scope,
        planBaseFields,
        planArrangementFields,
        planDescriptionField,
        planEditorVisible: false,
        planEditorIndex: -1,
        planEditorTitle: '',
        planDraft: null,
        'form.title': `确定${itinerary.title || '这个行程'}采用哪套方案`,
        'form.options': []
      });
    } catch (error) {
      const message = error.message || '关联行程加载失败';
      this.setData({ loadError: message });
      wx.showToast({ title: message, icon: 'none' });
    } finally {
      this.setData({ loadingTarget: false });
    }
  },

  retryTarget() {
    getItineraryTypeMetadata(true).catch(() => {});
    if (this.data.form.targetItineraryId) {
      this.loadTarget(this.data.form.targetItineraryId);
    } else {
      this.loadItineraryOptions();
    }
  },

  input(event) {
    this.setData({ [`form.${event.currentTarget.dataset.key}`]: event.detail.value });
  },

  chooseDecision(event) {
    const decisionIndex = Number(event.detail.value);
    this.setData({ decisionIndex, 'form.decisionType': GENERAL_DECISIONS[decisionIndex] });
  },

  chooseVoteType(event) {
    if (!this.data.isFullPlan) {
      this.setData({ 'form.voteType': event.currentTarget.dataset.value });
    }
  },

  toggleAllowModify(event) {
    this.setData({ 'form.allowModify': event.detail.value });
  },

  deadlineInput(event) {
    this.setData({ [`form.${event.currentTarget.dataset.key}`]: event.detail.value });
  },

  optionInput(event) {
    const { index, key } = event.currentTarget.dataset;
    this.setData({ [`form.options[${index}].${key}`]: event.detail.value });
  },

  addOption() {
    const index = this.data.form.options.length;
    if (this.data.isFullPlan) {
      this.setData({
        planEditorVisible: true,
        planEditorIndex: -1,
        planEditorTitle: planLabel(index),
        planDraft: copyCurrentPlan(this.data.target || {}, this.data.fullPlanScope, index)
      });
      return;
    }
    this.setData({ 'form.options': this.data.form.options.concat(emptyOption()) });
  },

  editPlanOption(event) {
    const index = Number(event.currentTarget.dataset.index);
    const option = this.data.form.options[index];
    if (!option) return;
    this.setData({
      planEditorVisible: true,
      planEditorIndex: index,
      planEditorTitle: planLabel(index),
      planDraft: { ...option }
    });
  },

  planDraftInput(event) {
    const { key } = event.currentTarget.dataset;
    this.setData({ [`planDraft.${key}`]: event.detail.value });
  },

  savePlanOption() {
    const draft = this.data.planDraft;
    if (!draft) return;
    const index = this.data.planEditorIndex >= 0
      ? this.data.planEditorIndex : this.data.form.options.length;
    const message = this.validateFullPlanOption(draft, index);
    if (message) {
      wx.showToast({ title: message, icon: 'none' });
      return;
    }
    const options = this.data.form.options.slice();
    if (this.data.planEditorIndex >= 0) {
      options[this.data.planEditorIndex] = { ...draft };
    } else {
      options.push({ ...draft });
    }
    this.setData({
      'form.options': options,
      planEditorVisible: false,
      planEditorIndex: -1,
      planEditorTitle: '',
      planDraft: null
    });
  },

  closePlanEditor() {
    this.setData({
      planEditorVisible: false,
      planEditorIndex: -1,
      planEditorTitle: '',
      planDraft: null
    });
  },

  noop() {
  },

  removeOption(event) {
    const index = Number(event.currentTarget.dataset.index);
    this.setData({ 'form.options': this.data.form.options.filter((_, itemIndex) => itemIndex !== index) });
  },

  buildDeadline() {
    const { deadlineDate, deadlineTime } = this.data.form;
    if (!deadlineDate && !deadlineTime) return null;
    if (!deadlineDate || !deadlineTime) return '';
    return `${deadlineDate}T${deadlineTime}:00`;
  },

  validateFullPlanOption(option, index) {
    if (!String(option.optionText || '').trim()) return `请填写方案 ${index + 1} 的方案名称`;
    if (!String(option.title || '').trim()) return `请填写方案 ${index + 1} 的行程标题`;
    if (!String(option.itineraryDate || '').trim()) return `请选择方案 ${index + 1} 的日期`;
    const start = String(option.startTime || '').trim();
    const end = String(option.endTime || '').trim();
    if (Boolean(start) !== Boolean(end)) return `方案 ${index + 1} 的开始和结束时间需要同时填写`;
    if (!this.data.target.allDay && start && end
      && this.data.target.itineraryType !== 'LODGING' && end <= start) {
      return `方案 ${index + 1} 的结束时间必须晚于开始时间`;
    }
    return '';
  },

  validate() {
    const { form } = this.data;
    if (!form.title.trim()) return '请填写投票问题';
    if (this.data.isFullPlan && form.options.length < 1) return '请至少添加一个候选方案';
    if (!this.data.isFullPlan && form.options.length < 2) return '请至少填写两个投票选项';
    if (!this.data.isFullPlan && form.options.filter((item) => item.optionText.trim()).length < 2) {
      return '请至少填写两个投票选项';
    }
    if (this.data.isFullPlan) {
      if (this.data.loadError || !this.data.fullPlanScope.length) return '完整方案尚未加载完成';
      for (let index = 0; index < form.options.length; index += 1) {
        const message = this.validateFullPlanOption(form.options[index], index);
        if (message) return message;
      }
    }
    if (this.buildDeadline() === '') return '请同时选择截止日期和时间';
    return '';
  },

  buildFullPlanPayload(option) {
    return this.data.fullPlanScope.reduce((payload, field) => {
      const value = String(option[field] == null ? '' : option[field]).trim();
      payload[field] = value || null;
      return payload;
    }, {});
  },

  async save() {
    if (this.data.saving) return;
    const message = this.validate();
    if (message) return wx.showToast({ title: message, icon: 'none' });
    const { form } = this.data;
    const sourceOptions = this.data.isFullPlan
      ? [copyOriginalPlan(this.data.target || {}, this.data.fullPlanScope), ...form.options]
      : form.options.filter((item) => item.optionText.trim());
    const options = sourceOptions.map((item) => ({
      optionText: item.optionText.trim(),
      optionDescription: String(item.optionDescription || '').trim(),
      resultPayload: this.data.isFullPlan ? this.buildFullPlanPayload(item) : {}
    }));
    this.setData({ saving: true });
    try {
      const response = await createPoll(this.data.activityId, {
        title: form.title.trim(),
        description: form.description.trim(),
        purpose: this.data.isFullPlan ? 'UPDATE_ITINERARY' : 'GENERAL',
        decisionType: this.data.isFullPlan ? 'FULL_PLAN' : form.decisionType,
        decisionScope: this.data.isFullPlan ? this.data.fullPlanScope : [],
        targetItineraryId: this.data.isFullPlan ? form.targetItineraryId : null,
        voteType: this.data.isFullPlan ? 'SINGLE' : form.voteType,
        allowModify: form.allowModify,
        deadline: this.buildDeadline(),
        itineraryTemplate: {},
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
