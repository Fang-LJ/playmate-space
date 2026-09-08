const { createExpenseContext } = require('../../utils/expense-context');
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
    bookId: '',
    ready: false,
    loadError: '',
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
    ratioTotal: '0',
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
    this.expenseContext = createExpenseContext(options);
    this.setData({
      bookId: options.bookId || '',
      activityId: options.activityId || '',
      expenseId: options.expenseId || '',
      clientRequestId: options.expenseId ? '' : `expense-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
    });
    try {
      const { members, activity, currentUser } = await this.expenseContext.loadEditor();
      const now = this.datetimeParts(new Date());
      let form = {
        ...this.data.form,
        expenseTime: this.composeDatetime(now.date, now.time),
        payerUserId: currentUser.userId,
        shares: members.map(item => ({ userId: item.userId, checked: true, shareAmount: '', splitRatio: '1' }))
      };
      let expenseDate = now.date;
      let expenseClock = now.time;
      let optionalExpanded = false;
      if (this.data.expenseId) {
        const detail = await this.expenseContext.getExpense(this.data.expenseId);
        if (detail.canEdit === false) throw new Error('当前消费不可编辑，请返回账本查看');
        const parts = this.parseDatetime(detail.expenseTime);
        expenseDate = parts.date;
        expenseClock = parts.time;
        optionalExpanded = !!(detail.receiptUrl || detail.description);
        form = {
          ...detail,
          expenseTime: this.composeDatetime(expenseDate, expenseClock),
          shares: members.map(member => {
            const share = (detail.shares || []).find(item => item.userId === member.userId);
            return {
              userId: member.userId,
              checked: !!share,
              shareAmount: share ? String(share.shareAmount) : '',
              splitRatio: share && detail.splitMode === 'PROPORTIONAL' ? String(share.splitRatio || 1) : '1'
            };
          }),
          receiptUrl: detail.receiptUrl || ''
        };
      }
      const payerIndex = Math.max(0, members.findIndex(item => item.userId === form.payerUserId));
      const categoryIndex = Math.max(0, CATEGORIES.findIndex(item => item.value === form.category));
      this.setData({
        ready: true,
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
      this.setData({ loadError: error.message || '数据加载失败', ready: false });
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
    const splitMode = event.currentTarget.dataset.mode;
    const shares = this.data.form.shares.map(item => ({ ...item, splitRatio: item.splitRatio || '1' }));
    this.setData({ 'form.splitMode': splitMode, 'form.shares': shares }, () => this.updateAllocation());
  },

  toggleShare(event) {
    const index = Number(event.currentTarget.dataset.index);
    this.setData({ [`form.shares[${index}].checked`]: !this.data.form.shares[index].checked }, () => this.updateAllocation());
  },

  shareAmount(event) {
    this.setData({ [`form.shares[${event.currentTarget.dataset.index}].shareAmount`]: event.detail.value }, () => this.updateAllocation());
  },

  ratioInput(event) {
    this.setData({ [`form.shares[${event.currentTarget.dataset.index}].splitRatio`]: event.detail.value }, () => this.updateAllocation());
  },

  ratioBlur(event) {
    const value = event.detail.value;
    if (value && !this.isPositiveRatio(value)) wx.showToast({ title: '分摊比例必须大于 0', icon: 'none' });
  },

  updateAllocation() {
    const totalCents = this.toCents(this.data.form.amount);
    const selectedShares = this.data.form.shares.filter(item => item.checked);
    const allocatedCents = selectedShares
      .reduce((sum, item) => sum + (this.toCents(item.shareAmount) || 0), 0);
    const ratioTotal = selectedShares.reduce((sum, item) => sum + (this.toRatio(item.splitRatio) || 0), 0);
    this.setData({
      allocatedAmount: (allocatedCents / 100).toFixed(2),
      allocationBalanced: totalCents !== null && totalCents > 0 && allocatedCents === totalCents,
      ratioTotal: this.formatRatio(ratioTotal)
    });
  },

  toCents(value) {
    const text = String(value == null ? '' : value).trim();
    if (!/^\d+(?:\.\d{1,2})?$/.test(text)) return null;
    return Math.round(Number(text) * 100);
  },

  toRatio(value) {
    const text = String(value == null ? '' : value).trim();
    if (!/^\d+(?:\.\d{1,4})?$/.test(text)) return null;
    const result = Number(text);
    return result > 0 ? result : null;
  },

  isPositiveRatio(value) { return this.toRatio(value) !== null; },

  formatRatio(value) { return Number(value || 0).toFixed(4).replace(/\.?0+$/, ''); },

  toggleOptional() {
    this.setData({ optionalExpanded: !this.data.optionalExpanded });
  },

  async receipt() {
    if (this.data.uploading || this.data.saving) return;
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
    if (!this.data.ready || this.data.saving || this.data.uploading) return;
    const form = this.data.form;
    const amount = String(form.amount || '').trim();
    const shares = form.shares.filter(item => item.checked).map(item => ({
      userId: item.userId,
      shareAmount: form.splitMode === 'CUSTOM' ? String(item.shareAmount || '').trim() : null,
      splitRatio: form.splitMode === 'PROPORTIONAL' ? String(item.splitRatio || '').trim() : null
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
    if (form.splitMode === 'PROPORTIONAL' && shares.some(item => !this.isPositiveRatio(item.splitRatio))) {
      wx.showToast({ title: '分摊比例必须大于 0', icon: 'none' });
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
      await this.expenseContext.saveExpense(payload, this.data.expenseId);
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
