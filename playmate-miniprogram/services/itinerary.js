const { request } = require('../utils/request');
const base = (activityId) => `/api/activities/${activityId}/itineraries`;
const getItineraries = (activityId) => request({ url: base(activityId) });
const getItineraryDetail = (activityId, itineraryId) => request({ url: `${base(activityId)}/${itineraryId}` });
const createItinerary = (activityId, data) => request({ url: base(activityId), method: 'POST', data });
const updateItinerary = (activityId, itineraryId, data) => request({ url: `${base(activityId)}/${itineraryId}`, method: 'PUT', data });
const cancelItinerary = (activityId, itineraryId) => request({ url: `${base(activityId)}/${itineraryId}/cancel`, method: 'POST' });
module.exports = { getItineraries, getItineraryDetail, createItinerary, updateItinerary, cancelItinerary };
