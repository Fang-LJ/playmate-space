const { formatDateTime } = require('./itinerary-display');

const TODO_TYPE = {
  ITINERARY_STARTS_SOON: '即将开始',
  ITINERARY_IN_PROGRESS: '进行中',
  MANUAL_REMINDER: '活动提醒'
};

function isVisibleTodo(todo) {
  if (!todo) return false;
  const categories = [todo.targetType, todo.sourceType, todo.todoType, todo.type, todo.actionType, todo.sourceKey];
  return !categories.some((value) => /POLL|VOTE/i.test(String(value || '')));
}

function visibleTodos(todos) {
  return (Array.isArray(todos) ? todos : []).filter(isVisibleTodo);
}

module.exports = { TODO_TYPE, formatDateTime, isVisibleTodo, visibleTodos };
