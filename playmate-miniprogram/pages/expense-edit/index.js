const expense = require('../../services/expense');
const { getActivityDetail } = require('../../services/activity');
const { getCurrentUser } = require('../../services/user');
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
  data: {
    activityId: '',
    expenseId: '',
    members: [],
    currentUserId: '',
    isCreator: false,
    payerIndex: 0,
    categoryIndex: 0,
    categoryOptions: CATEGORIES,
    expenseDate: '',
    expenseClock: '',
    optionalExpanded: false,
    allocatedAmount: '0.00',
    allocationBalanced: false,
    form: {
      title: '', category: 'FOOD', amount: '', payerUserId: '', expenseTime: '',
      splitMode: 'EQUAL', shares: [], receiptFileId: null, receiptUrl: '',
      description: '', version: null
    },
    saving: false,
    uploading: false,
    clientRequestId: ''
  },

  async onLoad(options) {
    this.setData({
      activityId: options.activityId || '',
      expenseId: options.expenseId || '',
      clientRequestId: options.expenseId ? '' : `expense-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
    });
    try {
      const [members, activity, currentUser] = await Promise.all([
        expense.getExpenseMembers(this.data.activityId),
        getActivityDetail(this.data.activityId),
        getCurrentUser()
      ]);
      const now = this.datetimeParts(new Date());
      let form = {
        ...this.data.form,
        expenseTime: this.composeDatetime(now.date, now.time),
        payerUserId: currentUser.userId,
        shares: members.map(item => ({ userId: item.userId, checked: true, shareAmount: '' }))
      };
      let expenseDate = now.date;
      let expenseClock = now.time;
      let optionalExpanded = false;
      if (this.data.expenseId) {
        const detail = await expense.getExpense(this.data.activityId, this.data.expenseId);
        const parts = this.parseDatetime(detail.expenseTime);
        expenseDate = parts.date;
        expenseClock = parts.time;
        optionalExpanded = !!(detail.receiptUrl || detail.description);
        form = {
          ...detail,
          expenseTime: this.composeDatetime(expenseDate, expenseClock),
          shares: members.map(member => {
            const share = (detail.shares || []).find(item => item.userId === member.userId);
            return { userId: member.userId, checked: !!share, shareAmount: share ? String(share.shareAmount) : '' };
          }),
          receiptUrl: detail.receiptUrl || ''
        };
      }
      const payerIndex = Math.max(0, members.findIndex(item => item.userId === form.payerUserId));
      const categoryIndex = Math.max(0, CATEGORIES.findIndex(item => item.value === form.category));
      this.setData({
        members,
        currentUserId: currentUser.userId,
        isCreator: activity.currentUserRole === 'CREATOR',
        form,
        payerIndex,
        categoryIndex,
        expenseDate,
        expenseClock,
        optionalExpanded
      }, () => this.updateAllocation());
    } catch (error) {
      wx.showToast({ title: error.message || '数据加载失败', icon: 'none' });
    }
  },

  datetimeParts(date) {
    const pad = value => String(value).padStart(2, '0');
    return {
      date: `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`,
      time: `${pad(date.getHours())}:${pad(date.getMinutes())}`
    };
  },

  parseDatetime(value) {
    const text = String(value || '').replace(' ', 'T');
    const [date = '', rawTime = '00:00'] = text.split('T');
    return { date, time: rawTime.slice(0, 5) };
  },

  composeDatetime(date, time) {
    return `${date}T${time}:00`;
  },

  input(event) {
    const key = event.currentTarget.dataset.key;
    this.setData({ [`form.${key}`]: event.detail.value });
  },

  amountInput(event) {
    this.setData({ 'form.amount': event.detail.value }, () => this.updateAllocation());
  },

  category(event) {
    const categoryIndex = Number(event.detail.value);
    this.setData({ categoryIndex, 'form.category': CATEGORIES[categoryIndex].value });
  },

  payer(event) {
    if (!this.data.isCreator) return;
    const payerIndex = Number(event.detail.value);
    this.setData({ payerIndex, 'form.payerUserId': this.data.members[payerIndex].userId });
  },

  dateChange(event) {
    const expenseDate = event.detail.value;
    this.setData({ expenseDate, 'form.expenseTime': this.composeDatetime(expenseDate, this.data.expenseClock) });
  },

  timeChange(event) {
    const expenseClock = event.detail.value;
    this.setData({ expenseClock, 'form.expenseTime': this.composeDatetime(this.data.expenseDate, expenseClock) });
  },

  mode(event) {
    this.setData({ 'form.splitMode': event.currentTarget.dataset.mode }, () => this.updateAllocation());
  },

  toggleShare(event) {
    const index = Number(event.currentTarget.dataset.index);
    this.setData({ [`form.shares[${index}].checked`]: !this.data.form.shares[index].checked }, () => this.updateAllocation());
  },

  shareAmount(event) {
    this.setData({ [`form.shares[${event.currentTarget.dataset.index}].shareAmount`]: event.detail.value }, () => this.updateAllocation());
  },

  updateAllocation() {
    const totalCents = this.toCents(this.data.form.amount);
    const allocatedCents = this.data.form.shares
      .filter(item => item.checked)
      .reduce((sum, item) => sum + (this.toCents(item.shareAmount) || 0), 0);
    this.setData({
      allocatedAmount: (allocatedCents / 100).toFixed(2),
      allocationBalanced: totalCents !== null && totalCents > 0 && allocatedCents === totalCents
    });
  },

  toCents(value) {
    const text = String(value == null ? '' : value).trim();
    if (!/^\d+(?:\.\d{1,2})?$/.test(text)) return null;
    return Math.round(Number(text) * 100);
  },

  toggleOptional() {
    this.setData({ optionalExpanded: !this.data.optionalExpanded });
  },

  async receipt() {
    try {
      this.setData({ uploading: true });
      const path = await chooseImage();
      const file = await uploadExpenseReceipt(path);
      this.setData({ 'form.receiptFileId': file.fileId, 'form.receiptUrl': file.url });
    } catch (error) {
      wx.showToast({ title: error.message || '上传失败', icon: 'none' });
    } finally {
      this.setData({ uploading: false });
    }
  },

  async submit() {
    const form = this.data.form;
    const amount = String(form.amount || '').trim();
    const shares = form.shares.filter(item => item.checked).map(item => ({
      userId: item.userId,
      shareAmount: form.splitMode === 'CUSTOM' ? String(item.shareAmount || '').trim() : null
    }));
    if (!form.title.trim() || !amount || !shares.length) {
      wx.showToast({ title: '请填写账单并选择分摊成员', icon: 'none' });
      return;
    }
    if (this.toCents(amount) === null || this.toCents(amount) <= 0) {
      wx.showToast({ title: '请输入正确的金额', icon: 'none' });
      return;
    }
    if (form.splitMode === 'CUSTOM' && !this.data.allocationBalanced) {
      wx.showToast({ title: '自定义分摊金额之和必须等于总金额', icon: 'none' });
      return;
    }
    const payload = {
      ...form,
      title: form.title.trim(),
      amount,
      payerUserId: Number(form.payerUserId),
      shares,
      version: form.version,
      clientRequestId: this.data.expenseId ? null : this.data.clientRequestId
    };
    delete payload.receiptUrl;
    try {
      this.setData({ saving: true });
      await expense.saveExpense(this.data.activityId, payload, this.data.expenseId);
      wx.showToast({ title: '已保存', icon: 'success' });
      setTimeout(() => wx.navigateBack(), 500);
    } catch (error) {
      wx.showToast({ title: error.message || '保存失败', icon: 'none' });
    } finally {
      this.setData({ saving: false });
    }
  },

  back() { wx.navigateBack(); }
});
