const {
  ITINERARY_STATUS,
  ITINERARY_TYPE,
  formatTimeRange,
  hasVisibleText,
  itinerarySummary,
  label
} = require('./p1-display');

const TYPE_ORDER = ['TRANSPORT', 'MEAL', 'LODGING', 'SIGHTSEEING', 'ACTIVITY', 'OTHER'];

const TYPE_VISUAL = {
  TRANSPORT: { themeClass: 'transport', arrangementTitle: '交通安排' },
  MEAL: { themeClass: 'meal', arrangementTitle: '用餐安排' },
  LODGING: { themeClass: 'lodging', arrangementTitle: '住宿安排' },
  SIGHTSEEING: { themeClass: 'sightseeing', arrangementTitle: '景点安排' },
  ACTIVITY: { themeClass: 'activity', arrangementTitle: '活动安排' },
  OTHER: { themeClass: 'other', arrangementTitle: '其他安排' }
};

const BASE_FIELD_KEYS = ['title', 'itineraryDate', 'startTime', 'endTime'];
const ALWAYS_PRESERVED_KEYS = new Set([...BASE_FIELD_KEYS, 'description']);
const FIELD_CONTROL = {
  itineraryDate: 'date',
  startTime: 'time',
  endTime: 'time',
  description: 'textarea'
};

function normalizeMetadata(metadata) {
  const source = Array.isArray(metadata) ? metadata : [];
  const byType = {};
  source.forEach((item) => {
    if (item && item.type) byType[item.type] = item;
  });
  return TYPE_ORDER.map((type) => byType[type]).filter(Boolean);
}

function metadataMap(metadata) {
  return normalizeMetadata(metadata).reduce((result, item) => {
    result[item.type] = item;
    return result;
  }, {});
}

function getTypeVisual(type) {
  return TYPE_VISUAL[type] || TYPE_VISUAL.OTHER;
}

function getTypeMetadata(metadata, type) {
  return metadataMap(metadata)[type] || null;
}

function typeLabel(metadata, type) {
  const definition = getTypeMetadata(metadata, type);
  return definition ? definition.label : label(ITINERARY_TYPE, type, '其他');
}

function trimSeconds(value) {
  return String(value || '').replace(/^(\d{2}:\d{2})(?::\d{2})?$/, '$1');
}

function formatHeroDate(itinerary) {
  const date = String(itinerary.itineraryDate || '');
  const parts = date.split('-').map(Number);
  const dateText = parts.length === 3 && parts.every(Number.isFinite)
    ? `${parts[1]}月${parts[2]}日`
    : date || '日期待定';
  if (itinerary.allDay) return `${dateText} · 请补充时间`;
  const start = trimSeconds(itinerary.startTime);
  const end = trimSeconds(itinerary.endTime);
  if (!start && !end) return dateText;
  const overnight = itinerary.itineraryType === 'LODGING' && start && end && end <= start;
  const timeText = start && end
    ? `${start}–${overnight ? '次日 ' : ''}${end}`
    : start || end;
  return `${dateText} · ${timeText}`;
}

function fieldValue(itinerary, key) {
  if (key === 'startTime') {
    const value = trimSeconds(itinerary.startTime);
    return itinerary.itineraryType === 'LODGING' && value ? `${value} 后` : value;
  }
  if (key === 'endTime') {
    const value = trimSeconds(itinerary.endTime);
    const start = trimSeconds(itinerary.startTime);
    const overnight = itinerary.itineraryType === 'LODGING' && start && value && value <= start;
    return overnight ? `次日 ${value}` : value;
  }
  return itinerary[key];
}

function buildCardViewModel(itinerary, metadata) {
  const visual = getTypeVisual(itinerary.itineraryType);
  return {
    ...itinerary,
    timeText: formatTimeRange(itinerary),
    statusText: label(ITINERARY_STATUS, itinerary.planningStatus),
    statusClass: String(itinerary.planningStatus || '').toLowerCase(),
    typeText: typeLabel(metadata, itinerary.itineraryType),
    themeClass: visual.themeClass,
    summaryText: itinerary.displaySummary || itinerarySummary(itinerary),
    descriptionText: hasVisibleText(itinerary.description) ? String(itinerary.description).trim() : ''
  };
}

function uniqueFields(fields) {
  const seen = new Set();
  return fields.filter((field) => {
    if (!field || !field.key || seen.has(field.key)) return false;
    seen.add(field.key);
    return true;
  });
}

function buildDetailViewModel(itinerary, metadata) {
  const definition = getTypeMetadata(metadata, itinerary.itineraryType);
  if (!definition) return null;
  const visual = getTypeVisual(itinerary.itineraryType);
  const detailFields = uniqueFields([
    ...(definition.focusFields || []),
    ...(definition.commonFields || []).filter((field) => field.key === 'address')
  ])
    .filter((field) => !['title', 'itineraryDate', 'description'].includes(field.key))
    .map((field) => ({ ...field, value: fieldValue(itinerary, field.key) }))
    .filter((field) => hasVisibleText(field.value));

  return {
    ...itinerary,
    typeText: definition.label,
    themeClass: visual.themeClass,
    arrangementTitle: visual.arrangementTitle,
    planningStatusText: label(ITINERARY_STATUS, itinerary.planningStatus),
    statusClass: String(itinerary.planningStatus || '').toLowerCase(),
    heroDateText: formatHeroDate(itinerary),
    summaryText: itinerary.displaySummary || itinerarySummary(itinerary),
    detailFields,
    descriptionText: hasVisibleText(itinerary.description) ? String(itinerary.description).trim() : ''
  };
}

function formField(field, form) {
  const key = field.key;
  const half = ['itineraryDate', 'startTime', 'endTime', 'mealType', 'restaurantName', 'departureName', 'destinationName'].includes(key);
  return {
    ...field,
    value: form[key] || '',
    control: FIELD_CONTROL[key] || 'input',
    layoutClass: half ? 'half' : 'full'
  };
}

function buildFormViewModel(metadata, type, form) {
  const definition = getTypeMetadata(metadata, type);
  if (!definition) return null;
  const allFields = uniqueFields([...(definition.focusFields || []), ...(definition.commonFields || [])]);
  const byKey = allFields.reduce((result, field) => {
    result[field.key] = field;
    return result;
  }, {});
  const commonFields = BASE_FIELD_KEYS
    .map((key) => byKey[key])
    .filter(Boolean)
    .map((field) => formField(field, form));
  const arrangementFields = allFields
    .filter((field) => !ALWAYS_PRESERVED_KEYS.has(field.key))
    .map((field) => formField(field, form));
  const descriptionField = byKey.description ? formField(byKey.description, form) : null;
  const visual = getTypeVisual(type);
  return {
    type,
    label: definition.label,
    themeClass: visual.themeClass,
    arrangementTitle: visual.arrangementTitle,
    commonFields,
    arrangementFields,
    descriptionField
  };
}

function typeOptions(metadata) {
  return normalizeMetadata(metadata).map((item) => ({
    value: item.type,
    label: item.label,
    themeClass: getTypeVisual(item.type).themeClass
  }));
}

function typeSpecificKeys(metadata) {
  const keys = new Set();
  normalizeMetadata(metadata).forEach((definition) => {
    [...(definition.focusFields || []), ...(definition.commonFields || [])].forEach((field) => {
      if (!ALWAYS_PRESERVED_KEYS.has(field.key)) keys.add(field.key);
    });
  });
  return Array.from(keys);
}

module.exports = {
  TYPE_ORDER,
  buildCardViewModel,
  buildDetailViewModel,
  buildFormViewModel,
  getTypeMetadata,
  normalizeMetadata,
  typeOptions,
  typeSpecificKeys
};
