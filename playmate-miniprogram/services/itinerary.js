const { request } = require('../utils/request');
const base = (activityId) => `/api/activities/${activityId}/itineraries`;
let typeMetadataPromise;

const getItineraryTypeMetadata = (force = false) => {
  if (force || !typeMetadataPromise) {
    typeMetadataPromise = request({ url: '/api/itineraries/type-metadata' })
      .catch((error) => {
        typeMetadataPromise = null;
        throw error;
      });
  }
  return typeMetadataPromise;
};
const getItineraries = (activityId, includeCanceled = false) => request({ url: base(activityId), data: { includeCanceled } });
const getItineraryDetail = (activityId, itineraryId) => request({ url: `${base(activityId)}/${itineraryId}` });
const createItinerary = (activityId, data) => request({ url: base(activityId), method: 'POST', data });
const updateItinerary = (activityId, itineraryId, data) => request({ url: `${base(activityId)}/${itineraryId}`, method: 'PUT', data });
const cancelItinerary = (activityId, itineraryId) => request({ url: `${base(activityId)}/${itineraryId}/cancel`, method: 'POST' });
const restoreItinerary = (activityId, itineraryId) => request({ url: `${base(activityId)}/${itineraryId}/restore`, method: 'POST' });
const deleteItinerary = (activityId, itineraryId) => request({ url: `${base(activityId)}/${itineraryId}`, method: 'DELETE' });
module.exports = {
  getItineraryTypeMetadata,
  getItineraries,
  getItineraryDetail,
  createItinerary,
  updateItinerary,
  cancelItinerary,
  restoreItinerary,
  deleteItinerary
};
