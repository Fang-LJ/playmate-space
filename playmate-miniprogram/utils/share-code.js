function normalizeShareCode(value) {
  return (value || '').trim().toUpperCase();
}

function buildInvitePath(shareCode) {
  const normalized = normalizeShareCode(shareCode);
  return normalized ? `/pages/activity-invite/index?code=${encodeURIComponent(normalized)}` : '';
}

module.exports = {
  normalizeShareCode,
  buildInvitePath
};
