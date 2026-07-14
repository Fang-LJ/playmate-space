const ITINERARY_TYPE = {
  TRANSPORT: '交通',
  MEAL: '用餐',
  LODGING: '住宿',
  SIGHTSEEING: '景点',
  ACTIVITY: '活动',
  OTHER: '其他'
};

const ITINERARY_STATUS = {
  DRAFT: '草稿',
  PENDING_DECISION: '待决定',
  CONFIRMED: '已确认',
  CANCELED: '已取消'
};

const POLL_PURPOSE = {
  GENERAL: '普通投票',
  UPDATE_ITINERARY: '确定已有行程',
  CREATE_ITINERARY: '投票后生成行程'
};

const POLL_DECISION = {
  PLACE: '地点',
  TIME: '时间',
  TRANSPORT: '交通方式',
  CONTENT: '活动内容',
  RESTAURANT: '餐厅',
  OTHER: '其他'
};

const POLL_STATUS = {
  DRAFT: '草稿',
  ACTIVE: '进行中',
  CLOSED: '已结束',
  CANCELED: '已取消'
};

const POLL_RESULT_STATUS = {
  NOT_REQUIRED: '无需应用',
  PENDING: '等待结果',
  APPLIED: '结果已应用',
  REVIEW_REQUIRED: '结果待确认',
  FAILED: '应用失败'
};

const TODO_TYPE = {
  POLL_PENDING: '待投票',
  POLL_DUE_SOON: '即将截止',
  POLL_REVIEW_REQUIRED: '待确认',
  ITINERARY_STARTS_SOON: '即将开始',
  ITINERARY_IN_PROGRESS: '进行中'
};

function label(map, value, fallback = '未设置') {
  return map[value] || fallback;
}

function formatDateTime(value) {
  if (!value || value === 'null' || value === 'undefined') return '';
  return String(value).replace('T', ' ').replace(/:00$/, '');
}

function formatTimeRange(itinerary) {
  if (!itinerary) return '时间待定';
  if (itinerary.allDay) return '请补充时间';
  const start = itinerary.startTime || '';
  const end = itinerary.endTime || '';
  if (start && end) return `${start}-${end}`;
  return start || end || '时间待定';
}

function hasVisibleText(value) {
  const text = String(value == null ? '' : value).trim();
  return Boolean(text && text !== 'null' && text !== 'undefined');
}

module.exports = {
  ITINERARY_TYPE,
  ITINERARY_STATUS,
  POLL_PURPOSE,
  POLL_DECISION,
  POLL_STATUS,
  POLL_RESULT_STATUS,
  TODO_TYPE,
  label,
  formatDateTime,
  formatTimeRange,
  hasVisibleText
};
