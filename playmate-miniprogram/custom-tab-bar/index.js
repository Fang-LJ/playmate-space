Component({
  data: {
    selected: 0,
    list: [
      { pagePath: '/pages/activity-list/index', text: '活动', key: 'activity' },
      { pagePath: '/pages/mine/index', text: '我的', key: 'mine' }
    ]
  },

  lifetimes: {
    attached() {
      this.syncSelected();
    }
  },

  methods: {
    syncSelected() {
      const pages = getCurrentPages();
      const current = pages[pages.length - 1];
      if (!current) return;
      const index = this.data.list.findIndex((item) => item.pagePath.slice(1) === current.route);
      if (index >= 0) this.setData({ selected: index });
    },

    switchTab(event) {
      const { index, path } = event.currentTarget.dataset;
      if (Number(index) === this.data.selected) return;
      this.setData({ selected: Number(index) });
      wx.switchTab({ url: path });
    }
  }
});
