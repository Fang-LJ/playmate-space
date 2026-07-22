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
  ROUTE: '出发地、目的地和路线',
  CONTENT: '活动内容',
  RESTAURANT: '餐厅',
  ITINERARY_NAME: '行程名称',
  OTHER: '其他'
};

const DECISION_SCOPE = {
  PLACE: ['locationName'],
  TIME: ['itineraryDate', 'startTime', 'endTime'],
  TRANSPORT: ['transportMode'],
  ROUTE: ['departureName', 'destinationName', 'routeDetail'],
  CONTENT: ['activityContent'],
  RESTAURANT: ['mealType', 'restaurantName', 'address'],
  ITINERARY_NAME: ['title'],
  OTHER: ['title']
};

const FIELD_LABEL = {
  title: '行程名称',
  itineraryDate: '日期',
  startTime: '开始时间',
  endTime: '结束时间',
  transportMode: '交通方式',
  departureName: '出发地',
  destinationName: '目的地',
  routeDetail: '路线',
  mealType: '用餐类型',
  restaurantName: '具体餐厅',
  address: '详细地址',
  activityContent: '活动内容',
  locationName: '地点'
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
  const formatTime = (value) => String(value || '').replace(/^(\d{2}:\d{2})(?::\d{2})?$/, '$1');
  const start = formatTime(itinerary.startTime);
  const end = formatTime(itinerary.endTime);
  if (start && end) return `${start}-${end}`;
  return start || end || '时间待定';
}

function hasVisibleText(value) {
  const text = String(value == null ? '' : value).trim();
  return Boolean(text && text !== 'null' && text !== 'undefined');
}

function itinerarySummary(itinerary) {
  if (!itinerary) return '具体方案待补充';
  if (itinerary.displaySummary) return itinerary.displaySummary;
  if (itinerary.planningStatus === 'PENDING_DECISION') return '具体方案待决定';
  const join = (...values) => values.filter(hasVisibleText).slice(0, 2).join(' · ');
  if (itinerary.itineraryType === 'TRANSPORT') {
    const route = itinerary.departureName && itinerary.destinationName
      ? `${itinerary.departureName} → ${itinerary.destinationName}`
      : itinerary.departureName || itinerary.destinationName;
    return join(itinerary.transportMode, route, itinerary.locationName) || '交通方案待补充';
  }
  if (itinerary.itineraryType === 'MEAL') {
    return join(itinerary.mealType, itinerary.restaurantName, itinerary.locationName) || '用餐方案待补充';
  }
  return join(itinerary.activityContent, itinerary.locationName) || '具体方案待补充';
}

function dateGroupMeta(date, count) {
  const parsed = new Date(`${date}T00:00:00`);
  const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
  return {
    date,
    weekday: Number.isNaN(parsed.getTime()) ? '' : weekdays[parsed.getDay()],
    countText: `${count} 项`
  };
}

module.exports = {
  ITINERARY_TYPE,
  ITINERARY_STATUS,
  POLL_PURPOSE,
  POLL_DECISION,
  DECISION_SCOPE,
  FIELD_LABEL,
  POLL_STATUS,
  POLL_RESULT_STATUS,
  TODO_TYPE,
  label,
  formatDateTime,
  formatTimeRange,
  hasVisibleText,
  itinerarySummary,
  dateGroupMeta
};
