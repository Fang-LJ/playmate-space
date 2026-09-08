function money(value) { return Number(value || 0).toFixed(2); }
function netText(value) { const n = Number(value || 0); return n > 0 ? `应收 ¥${money(n)}` : n < 0 ? `应付 ¥${money(-n)}` : '无需结算'; }
function requestId() { return `book-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`; }
function invitePath(code, memberId) {
  return `/pages/book-invite/index?code=${encodeURIComponent(code)}${memberId ? `&memberId=${encodeURIComponent(memberId)}` : ''}`;
}
function settlementText(dashboard) {
  const lines = [dashboard.book.name, `总支出 ¥${money(dashboard.totalAmount)} · ${dashboard.expenseCount} 笔消费`, ''];
  dashboard.suggestions.forEach(s => lines.push(`${s.fromNickname} → ${s.toNickname}：¥${money(s.amount)}`));
  if (!dashboard.suggestions.length) lines.push('当前无需结算');
  lines.push('', '以上为当前分账建议，未记录实际转账。');
  return lines.join('\n');
}
module.exports = { money, netText, requestId, invitePath, settlementText };
