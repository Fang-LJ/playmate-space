package com.playmate.space.service.photo;

import com.playmate.space.entity.ActivityPhotoAuditTaskEntity;
import com.playmate.space.entity.FileEntity;
import org.springframework.stereotype.Service;

@Service
public class WechatContentModerationService implements ContentModerationService {
    @Override public String provider() { return "WECHAT"; }
    @Override public ModerationOutcome submitImage(ActivityPhotoAuditTaskEntity task, FileEntity file) {
        // Round 2 intentionally does not hard-code a third-party callback/signature protocol.
        return new ModerationOutcome(ModerationOutcome.Type.ERROR, null, "WECHAT_NOT_CONFIGURED",
                "微信内容安全接口需在上线联调时按最新官方协议配置");
    }
}
