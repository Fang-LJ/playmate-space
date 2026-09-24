const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { createRequire } = require('node:module');
const { visibleTodos } = require('../utils/todo-display');
const originalDisplay = require('../utils/p1-display');
const itineraryDisplay = require('../utils/itinerary-display');

function page(name, mocks, wx = {}) {
  const file = path.resolve(__dirname, '../pages', name, 'index.js');
  let definition;
  vm.runInNewContext(fs.readFileSync(file, 'utf8'), {
    require: moduleName => moduleName in mocks ? mocks[moduleName] : createRequire(file)(moduleName),
    Page: value => { definition = value; },
    wx,
    setTimeout: callback => callback(),
    console
  });
  return {
    ...definition,
    data: structuredClone(definition.data),
    setData(patch) { Object.assign(this.data, patch); }
  };
}

const historicalTodos = [
  { activityId: 1, activityName: '聚餐', targetType: 'POLL', targetId: 10, todoType: 'POLL_VOTE', title: '参与投票' },
  { activityId: 1, activityName: '聚餐', targetType: 'ITINERARY', targetId: 20, todoType: 'ITINERARY_STARTS_SOON', title: '行程即将开始' },
  { activityId: 1, activityName: '聚餐', sourceType: 'POLL', sourceId: 11, todoType: 'POLL_RESULT_CONFIRM', title: '确认结果' },
  { activityId: 2, activityName: '旅行', targetType: 'MANUAL', targetId: 30, todoType: 'MANUAL_REMINDER', title: '出发提醒' }
];

test('shared itinerary display keeps the existing labels and formatting', () => {
  assert.deepEqual(itineraryDisplay.ITINERARY_STATUS, originalDisplay.ITINERARY_STATUS);
  assert.deepEqual(itineraryDisplay.ITINERARY_TYPE, originalDisplay.ITINERARY_TYPE);
  const itinerary = { itineraryType: 'TRANSPORT', planningStatus: 'CONFIRMED', transportMode: '地铁', departureName: '车站', destinationName: '餐厅', startTime: '18:00:00', endTime: '18:30:00' };
  assert.equal(itineraryDisplay.itinerarySummary(itinerary), originalDisplay.itinerarySummary(itinerary));
  assert.equal(itineraryDisplay.formatTimeRange(itinerary), originalDisplay.formatTimeRange(itinerary));
  assert.deepEqual(itineraryDisplay.dateGroupMeta('2026-09-24', 2), originalDisplay.dateGroupMeta('2026-09-24', 2));
});

test('historical poll tasks are excluded even when the backend reports a larger count', () => {
  assert.deepEqual(visibleTodos(historicalTodos).map(item => item.targetId), [20, 30]);
  assert.deepEqual(visibleTodos([{ targetType: 'ITINERARY', todoType: 'POLL_REVIEW_REQUIRED' }]), []);
});

test('activity list badge counts only visible tasks', async () => {
  const list = page('activity-list', {
    '../../services/auth': { isLoggedIn: () => true },
    '../../services/activity': { getMyActivities: async () => [] },
    '../../services/collaboration': { getMyActivityTodos: async () => ({ todoCount: 99, todos: historicalTodos }) }
  });
  assert.equal(await list.getTodoCount(), 2);
});

test('activity detail ignores poll defaults, counts and task navigation', async () => {
  const destinations = [];
  const detail = page('activity-detail', {
    '../../services/activity': { getActivityDetail: async () => ({ status: 'PLANNING', currentUserRole: 'CREATOR' }) },
    '../../services/itinerary': { getItineraries: async () => [] },
    '../../services/collaboration': {
      getSummary: async () => ({ defaultTab: 'POLLS', todoCount: 99, todos: historicalTodos.slice(0, 2), activePollCount: 7 }),
      getMyActivityTodos: async () => ({ todoCount: 99, todos: historicalTodos })
    },
    '../../services/member': { getActivityMembers: async () => [] },
    '../../services/user': { getCurrentUser: async () => ({ userId: 1 }) },
    '../../services/expense': { getExpenseSummary: async () => ({}) }
  }, { navigateTo: ({ url }) => destinations.push(url) });
  detail.setData({ activityId: '1', activeTab: 'POLLS' });
  await detail.load();
  assert.equal(detail.data.activeTab, 'ITINERARIES');
  assert.equal(detail.data.summary.todoCount, 1);
  assert.deepEqual(Array.from(detail.data.summary.todos, item => item.title), ['行程即将开始']);
  assert.equal('activePollCount' in detail.data.summary, false);
  detail.todo({ currentTarget: { dataset: { targetType: 'POLL', targetId: 10 } } });
  assert.deepEqual(destinations, []);
});

test('activity detail still filters summary tasks if the full todo request fails', async () => {
  const detail = page('activity-detail', {
    '../../services/activity': { getActivityDetail: async () => ({ status: 'PLANNING', currentUserRole: 'CREATOR' }) },
    '../../services/itinerary': { getItineraries: async () => [] },
    '../../services/collaboration': {
      getSummary: async () => ({ defaultTab: 'POLLS', todoCount: 99, todos: historicalTodos.slice(0, 2) }),
      getMyActivityTodos: async () => { throw new Error('Unavailable'); }
    },
    '../../services/member': { getActivityMembers: async () => [] },
    '../../services/user': { getCurrentUser: async () => ({ userId: 1 }) },
    '../../services/expense': { getExpenseSummary: async () => ({}) }
  });
  detail.setData({ activityId: '1' });
  await detail.load();
  assert.equal(detail.data.summary.todoCount, 1);
  assert.equal(detail.data.summary.todos[0].targetType, 'ITINERARY');
});

test('my todo page never displays or opens historical poll tasks', async () => {
  const destinations = [];
  const todos = page('activity-todos', {
    '../../services/collaboration': { getMyActivityTodos: async () => ({ todoCount: 99, todos: historicalTodos }) }
  }, { navigateTo: ({ url }) => destinations.push(url) });
  await todos.load();
  assert.equal(todos.data.groups.reduce((count, group) => count + group.todos.length, 0), 2);
  todos.openTodo({ currentTarget: { dataset: { activityId: 1, targetType: 'POLL', targetId: 10 } } });
  assert.deepEqual(destinations, []);
  todos.openTodo({ currentTarget: { dataset: { activityId: 1, targetType: 'ITINERARY', targetId: 20 } } });
  assert.match(destinations[0], /itinerary-detail/);
});

test('itinerary creation uses the direct path and detail excludes related polls', async () => {
  let payload;
  const editor = page('itinerary-edit', {
    '../../services/itinerary': { createItinerary: async (_id, data) => { payload = data; } }
  }, { showToast() {}, navigateBack() {} });
  editor.setData({ activityId: '1', form: {
    title: '晚餐', itineraryType: 'MEAL', itineraryDate: '2026-09-24', startTime: '18:00', endTime: '19:00'
  } });
  await editor.save();
  assert.equal(payload.creationMode, 'DIRECT');
  assert.equal('poll' in payload, false);

  const detail = page('itinerary-detail', {
    '../../services/itinerary': {
      getItineraryDetail: async () => ({ itinerary: { itineraryId: 20 }, relatedPolls: [{ pollId: 10 }] }),
      getItineraryTypeMetadata: async () => []
    },
    '../../utils/itinerary-ui': {
      buildDetailViewModel: itinerary => itinerary,
      normalizeMetadata: metadata => metadata
    }
  });
  detail.setData({ activityId: '1', itineraryId: '20' });
  await detail.load();
  assert.equal('relatedPolls' in detail.data.detail, false);
  assert.equal(typeof detail.newPoll, 'undefined');
});

test('a historical linked task cannot expose restricted service copy on delete', async () => {
  let confirm, toast;
  const detail = page('itinerary-detail', {
    '../../services/itinerary': {
      deleteItinerary: async () => { throw new Error('该行程存在未完成的关联投票'); }
    }
  }, {
    showModal: options => { confirm = options.success; },
    showToast: options => { toast = options.title; }
  });
  detail.setData({ activityId: '1', itineraryId: '20' });
  detail.remove();
  await confirm({ confirm: true });
  assert.equal(toast, '当前行程暂无法删除');
});
