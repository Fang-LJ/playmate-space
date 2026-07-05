const { getActivityMembers, removeActivityMember } = require('../../services/member');

const ROLE_LABELS = {
  CREATOR: '创建者',
  MEMBER: '成员'
};

Page({
  data: {
    activityId: '',
    loading: true,
    removingMemberId: '',
    members: [],
    myMember: null,
    isCreator: false,
    errorMessage: ''
  },

  onLoad(options) {
    this.setData({
      activityId: options.activityId || ''
    });
  },

  onShow() {
    if (this.data.activityId) {
      this.loadMembers();
    }
  },

  async loadMembers() {
    this.setData({
      loading: true,
      errorMessage: ''
    });
    try {
      const members = await getActivityMembers(this.data.activityId);
      const normalized = this.normalizeMembers(members || []);
      const myMember = normalized.find((member) => member.isCurrentUser) || null;
      const isCreator = Boolean(myMember && myMember.role === 'CREATOR');
      this.setData({
        members: normalized.map((member) => ({
          ...member,
          canRemove: isCreator && member.role === 'MEMBER' && !member.isCurrentUser
        })),
        myMember,
        isCreator
      });
    } catch (error) {
      this.setData({
        errorMessage: error.message || '成员列表加载失败'
      });
    } finally {
      this.setData({ loading: false });
    }
  },

  normalizeMembers(members) {
    return members.map((member) => ({
      ...member,
      roleText: ROLE_LABELS[member.role] || member.role || '成员',
      joinedText: this.formatJoinedTime(member.joinedTime),
      avatarText: this.resolveAvatarText(member.nickname)
    }));
  },

  formatJoinedTime(joinedTime) {
    if (!joinedTime) {
      return '加入时间未知';
    }
    return joinedTime.replace('T', ' ').slice(0, 16);
  },

  resolveAvatarText(nickname) {
    if (!nickname) {
      return '玩';
    }
    return nickname.slice(0, 1);
  },

  goEditNickname() {
    const nickname = this.data.myMember ? this.data.myMember.nickname || '' : '';
    wx.navigateTo({
      url: `/pages/member-nickname-edit/index?activityId=${this.data.activityId}&nickname=${encodeURIComponent(nickname)}`
    });
  },

  confirmRemove(event) {
    const { memberId, nickname } = event.currentTarget.dataset;
    if (!memberId) {
      return;
    }
    wx.showModal({
      title: '移除成员',
      content: `确定移除 ${nickname || '该成员'} 吗？移除后该成员将不能继续查看活动，也不能通过分享链接重新加入。`,
      confirmText: '移除',
      confirmColor: '#D94C4C',
      success: async (res) => {
        if (!res.confirm) {
          return;
        }
        await this.removeMember(memberId);
      }
    });
  },

  async removeMember(memberId) {
    if (this.data.removingMemberId) {
      return;
    }
    this.setData({ removingMemberId: String(memberId) });
    try {
      await removeActivityMember(this.data.activityId, memberId);
      wx.showToast({
        title: '已移除',
        icon: 'success'
      });
      await this.loadMembers();
    } catch (error) {
      wx.showToast({
        title: error.message || '移除失败',
        icon: 'none'
      });
    } finally {
      this.setData({ removingMemberId: '' });
    }
  },

  retryLoad() {
    this.loadMembers();
  },

  goActivityList() {
    wx.switchTab({
      url: '/pages/activity-list/index'
    });
  }
});
