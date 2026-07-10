const { logout, isLoggedIn } = require('../../services/auth');
const { getCurrentUser } = require('../../services/user');

const WECHAT_PROFILE_SUGGESTION_KEY = 'PLAYMATE_WECHAT_PROFILE_SUGGESTION';

function hasWechatProfileSuggestion(userId) {
  const suggestion = wx.getStorageSync(WECHAT_PROFILE_SUGGESTION_KEY);
  return Boolean(suggestion && suggestion.show && String(suggestion.userId) === String(userId));
}

Page({
  data: {
    loading: false,
    user: null,
    isLoggedIn: false
  },

  onShow() {
    this.loadCurrentUser();
  },

  async loadCurrentUser() {
    if (!isLoggedIn()) {
      this.setLoggedOut();
      return;
    }

    this.setData({ loading: true });
    try {
      const user = await getCurrentUser();
      const normalizedUser = this.normalizeUser(user);
      this.setData({
        user: normalizedUser,
        isLoggedIn: true,
        showWechatProfileSuggestion: hasWechatProfileSuggestion(normalizedUser.userId)
      });
    } catch (error) {
      this.setLoggedOut();
    } finally {
      this.setData({ loading: false });
    }
  },

  setLoggedOut() {
    this.setData({
      user: null,
      isLoggedIn: false,
      showWechatProfileSuggestion: false
    });
  },

  goLogin() {
    wx.navigateTo({
      url: '/pages/login/index'
    });
  },

  clearLogin() {
    const accountProtected = this.data.user && this.data.user.accountProtected;
    wx.showModal({
      title: '退出登录',
      content: accountProtected
        ? '确定退出当前账号吗？'
        : '当前账号还没有设置手机号或邮箱及密码。你仍可使用当前微信重新登录，也可以先设置其他登录方式。',
      confirmText: '继续退出',
      cancelText: accountProtected ? '取消' : '先去完善',
      success: (result) => {
        if (result.confirm) {
          logout();
          this.setLoggedOut();
          wx.showToast({ title: '已退出登录', icon: 'none' });
          return;
        }
        if (!accountProtected) {
          this.goAccountComplete();
        }
      }
    });
  },

  goActivities() {
    wx.switchTab({
      url: '/pages/activity-list/index'
    });
  },

  goCreateActivity() {
    wx.navigateTo({
      url: '/pages/activity-create/index'
    });
  },

  goProfileEdit() {
    wx.navigateTo({
      url: '/pages/profile-edit/index'
    });
  },

  goAccountComplete() {
    wx.navigateTo({
      url: '/pages/account-complete/index'
    });
  },

  goWechatProfile() {
    wx.navigateTo({ url: '/pages/wechat-profile/index' });
  },

  handleProfileCardTap() {
    if (this.data.isLoggedIn) {
      this.goProfileEdit();
      return;
    }
    this.goLogin();
  },

  normalizeUser(user) {
    return {
      ...user,
      nickname: user.nickname || '玩伴用户',
      contactText: user.phone || user.email || '未填写',
      passwordText: user.accountProtected ? '已设置备用登录方式' : '尚未设置备用登录方式',
      profileText: user.profileComplete ? '头像昵称已补充' : '头像昵称待补充',
      statusText: user.status === 'DISABLED' ? '已禁用' : '正常'
    };
  }
});
