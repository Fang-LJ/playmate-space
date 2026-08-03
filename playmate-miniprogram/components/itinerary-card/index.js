Component({
  properties: {
    item: {
      type: Object,
      value: {}
    }
  },
  methods: {
    select() {
      this.triggerEvent('select', { itineraryId: this.data.item.itineraryId });
    }
  }
});
