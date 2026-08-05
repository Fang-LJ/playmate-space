Component({
  properties: {
    item: {
      type: Object,
      value: {}
    },
    canEdit: {
      type: Boolean,
      value: false
    },
    canDelete: {
      type: Boolean,
      value: false
    },
    opened: {
      type: Boolean,
      value: false,
      observer(value) {
        this.setData({ swipeOffset: value ? -300 : 0 });
      }
    }
  },
  data: {
    swipeOffset: 0,
    touchStartX: 0,
    touchStartY: 0,
    initialSwipeOffset: 0,
    swiping: false
  },
  methods: {
    select() {
      if (this.data.swipeOffset < 0) {
        this.triggerEvent('swipeclose');
        return;
      }
      this.triggerEvent('select', { itineraryId: this.data.item.itineraryId });
    },
    touchStart(event) {
      const touch = event.touches && event.touches[0];
      if (!touch || (!this.data.canEdit && !this.data.canDelete)) return;
      this.setData({
        touchStartX: touch.pageX,
        touchStartY: touch.pageY,
        initialSwipeOffset: this.data.swipeOffset,
        swiping: false
      });
    },
    touchMove(event) {
      const touch = event.touches && event.touches[0];
      if (!touch || (!this.data.canEdit && !this.data.canDelete)) return;
      const deltaX = touch.pageX - this.data.touchStartX;
      const deltaY = touch.pageY - this.data.touchStartY;
      if (!this.data.swiping && Math.abs(deltaY) > Math.abs(deltaX)) return;
      if (Math.abs(deltaX) < 8 && !this.data.swiping) return;
      const width = wx.getSystemInfoSync().windowWidth || 375;
      const offset = Math.max(-300, Math.min(0, Math.round(this.data.initialSwipeOffset + deltaX * 750 / width)));
      this.setData({ swipeOffset: offset, swiping: true });
    },
    touchEnd() {
      if (!this.data.swiping) return;
      const opened = this.data.swipeOffset <= -120;
      this.setData({ swipeOffset: opened ? -300 : 0, swiping: false });
      this.triggerEvent(opened ? 'swipeopen' : 'swipeclose', { itineraryId: this.data.item.itineraryId });
    },
    edit(event) {
      event && event.stopPropagation && event.stopPropagation();
      this.triggerEvent('edit', { itineraryId: this.data.item.itineraryId });
    },
    remove(event) {
      event && event.stopPropagation && event.stopPropagation();
      this.triggerEvent('delete', { itineraryId: this.data.item.itineraryId });
    }
  }
});
