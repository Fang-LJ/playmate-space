const books = require('../../services/book');
const { requestId } = require('../../utils/book-ui');
Page({
  data: { name: '', namesText: '', saving: false },
  onLoad() { const date = new Date(); this.clientRequestId = requestId(); this.setData({ name: `${date.getMonth()+1}月${date.getDate()}日聚餐` }); },
  input(event) { this.setData({ [event.currentTarget.dataset.key]: event.detail.value }); },
  async create() {
    if (this.data.saving) return;
    const name = this.data.name.trim();
    const names = this.data.namesText.split(/[\n,，、]/).map(n => n.trim()).filter(Boolean);
    if (!name || name.length > 80 || names.length > 49 || names.some(n => n.length > 40)) { wx.showToast({ title: '请填写名称，最多添加49位朋友', icon: 'none' }); return; }
    this.setData({ saving: true });
    try { const book = await books.create({ name, names, clientRequestId: this.clientRequestId }); wx.redirectTo({ url: `/pages/book-detail/index?bookId=${book.bookId}` }); }
    catch (error) { wx.showToast({ title: error.message || '创建失败', icon: 'none' }); }
    finally { this.setData({ saving: false }); }
  }
});
