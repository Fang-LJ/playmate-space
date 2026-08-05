const expense = require('../../services/expense');
const { chooseImage, uploadExpenseReceipt } = require('../../services/file');

const CATEGORIES = [
  { value: 'FOOD', label: '餐饮' },
  { value: 'TRANSPORT', label: '交通' },
  { value: 'LODGING', label: '住宿' },
  { value: 'TICKET', label: '门票' },
  { value: 'ENTERTAINMENT', label: '娱乐' },
  { value: 'SHOPPING', label: '购物' },
  { value: 'OTHER', label: '其他' }
];
Page({
  data: { activityId: '', expenseId: '', members: [], payerIndex: 0, categoryIndex: 0, categoryOptions: CATEGORIES, form: { title: '', category: 'FOOD', amount: '', payerUserId: '', expenseTime: '', splitMode: 'EQUAL', shares: [], receiptFileId: null, receiptUrl: '', description: '', version: null }, saving: false, uploading: false },
  async onLoad(options) { this.setData({ activityId: options.activityId || '', expenseId: options.expenseId || '' }); try { const members = await expense.getExpenseMembers(this.data.activityId); const now = this.datetimeValue(new Date()); let form = { ...this.data.form, expenseTime: now, payerUserId: members[0] && members[0].userId, shares: members.map(item => ({ userId: item.userId, checked: true, shareAmount: '' })) }; if (this.data.expenseId) { const detail = await expense.getExpense(this.data.activityId, this.data.expenseId); form = { ...detail, shares: members.map(member => { const share = (detail.shares || []).find(item => item.userId === member.userId); return { userId: member.userId, checked: !!share, shareAmount: share ? String(share.shareAmount) : '' }; }), receiptUrl: detail.receiptUrl || '' }; } const payerIndex = Math.max(0, members.findIndex(item => item.userId === form.payerUserId)); const categoryIndex = Math.max(0, CATEGORIES.findIndex(item => item.value === form.category)); this.setData({ members, form, payerIndex, categoryIndex }); } catch (error) { wx.showToast({ title: error.message || '数据加载失败', icon: 'none' }); } },
  datetimeValue(date) { const p = n => String(n).padStart(2, '0'); return `${date.getFullYear()}-${p(date.getMonth()+1)}-${p(date.getDate())}T${p(date.getHours())}:${p(date.getMinutes())}:00`; },
  input(e) { const key = e.currentTarget.dataset.key; this.setData({ [`form.${key}`]: e.detail.value }); },
  category(e) { const categoryIndex = Number(e.detail.value); this.setData({ categoryIndex, 'form.category': CATEGORIES[categoryIndex].value }); },
  payer(e) { const payerIndex = Number(e.detail.value); this.setData({ payerIndex, 'form.payerUserId': this.data.members[payerIndex].userId }); },
  mode(e) { this.setData({ 'form.splitMode': e.currentTarget.dataset.mode }); },
  toggleShare(e) { const index = Number(e.currentTarget.dataset.index); this.setData({ [`form.shares[${index}].checked`]: !this.data.form.shares[index].checked }); },
  shareAmount(e) { this.setData({ [`form.shares[${e.currentTarget.dataset.index}].shareAmount`]: e.detail.value }); },
  async receipt() { try { this.setData({ uploading: true }); const path = await chooseImage(); const file = await uploadExpenseReceipt(path); this.setData({ 'form.receiptFileId': file.fileId, 'form.receiptUrl': file.url }); } catch (error) { wx.showToast({ title: error.message || '上传失败', icon: 'none' }); } finally { this.setData({ uploading: false }); } },
  async submit() { const form = this.data.form; const amount = String(form.amount || '').trim(); const shares = form.shares.filter(item => item.checked).map(item => ({ userId: item.userId, shareAmount: form.splitMode === 'CUSTOM' ? String(item.shareAmount || '').trim() : null })); if (!form.title || !amount || !shares.length) { wx.showToast({ title: '请填写账单并选择分摊成员', icon: 'none' }); return; } if (!/^\d+(?:\.\d{1,2})?$/.test(amount) || Number(amount) <= 0) { wx.showToast({ title: '请输入正确的金额', icon: 'none' }); return; } if (form.splitMode === 'CUSTOM' && shares.some(item => !/^\d+(?:\.\d{1,2})?$/.test(item.shareAmount))) { wx.showToast({ title: '请填写每位成员的分摊金额', icon: 'none' }); return; } const payload = { ...form, amount, payerUserId: Number(form.payerUserId), shares, version: form.version }; delete payload.receiptUrl; try { this.setData({ saving: true }); await expense.saveExpense(this.data.activityId, payload, this.data.expenseId); wx.showToast({ title: '已保存', icon: 'success' }); setTimeout(() => wx.navigateBack(), 500); } catch (error) { wx.showToast({ title: error.message || '保存失败', icon: 'none' }); } finally { this.setData({ saving: false }); } },
  back() { wx.navigateBack(); }
});
