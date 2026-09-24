const { request } = require('../utils/request');

const getSummary = (activityId) => request({ url: `/api/activities/${activityId}/collaboration-summary` });
const getMyActivityTodos = () => request({ url: '/api/users/me/activity-todos' });

module.exports = { getSummary, getMyActivityTodos };
