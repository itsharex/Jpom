/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package org.dromara.jpom.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.keepbx.jpom.IJsonMessage;
import cn.keepbx.jpom.model.JsonMessage;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.dromara.jpom.common.BaseServerController;
import org.dromara.jpom.common.Const;
import org.dromara.jpom.common.interceptor.LoginInterceptor;
import org.dromara.jpom.common.interceptor.NotLogin;
import org.dromara.jpom.common.validator.ValidatorConfig;
import org.dromara.jpom.common.validator.ValidatorItem;
import org.dromara.jpom.common.validator.ValidatorRule;
import org.dromara.jpom.controller.user.UserWorkspaceModel;
import org.dromara.jpom.model.dto.UserLoginDto;
import org.dromara.jpom.model.user.UserModel;
import org.dromara.jpom.service.user.UserBindWorkspaceService;
import org.dromara.jpom.service.user.UserService;
import org.dromara.jpom.util.TwoFactorAuthUtils;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 初始化程序
 *
 * @author bwcx_jzy
 * @since 2019/2/22
 */
@RestController
@Slf4j
public class InstallController extends BaseServerController {


    private final UserService userService;
    private final UserBindWorkspaceService userBindWorkspaceService;

    public InstallController(UserService userService,
                             UserBindWorkspaceService userBindWorkspaceService) {
        this.userService = userService;
        this.userBindWorkspaceService = userBindWorkspaceService;
    }

    /**
     * 初始化提交
     *
     * @param userName 系统管理员登录名
     * @param userPwd  系统管理员的登录密码
     * @return json
     * @api {post} install_submit.json 初始化提交
     * @apiGroup index
     * @apiUse defResultJson
     * @apiParam {String} userName 系统管理员登录名
     * @apiParam {String} userPwd 设置的登录密码 sha1 后传入
     * @apiSuccess {JSON}  data.tokenData token 相关信息
     * @apiSuccess {String}  data.mfaKey 二次验证的key
     * @apiSuccess {String}  data.url 二次验证的二维码相关字符串用户快速扫码导入
     */
    @PostMapping(value = "install_submit.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @NotLogin
    public IJsonMessage<JSONObject> installSubmit(
        @ValidatorConfig(value = {
            @ValidatorItem(value = ValidatorRule.NOT_EMPTY, msg = "登录名不能为空"),
            @ValidatorItem(value = ValidatorRule.NOT_BLANK, range = UserModel.USER_NAME_MIN_LEN + ":" + Const.ID_MAX_LEN, msg = "登录名长度范围" + UserModel.USER_NAME_MIN_LEN + "-" + Const.ID_MAX_LEN),
            @ValidatorItem(value = ValidatorRule.WORD, msg = "登录名不能包含汉字并且不能包含特殊字符")
        }) String userName,
        @ValidatorItem(value = ValidatorRule.NOT_BLANK, msg = "密码不能为空") String userPwd) {
        //
        Assert.state(!userService.canUse(), "系统已经初始化过啦，请勿重复初始化");

        boolean systemOccupyUserName = StrUtil.equalsAnyIgnoreCase(userName, UserModel.DEMO_USER, Const.SYSTEM_ID, UserModel.SYSTEM_ADMIN);
        Assert.state(!systemOccupyUserName, "当前登录名已经被系统占用啦");
        // 创建用户
        UserModel userModel = new UserModel();
        userModel.setName(UserModel.SYSTEM_OCCUPY_NAME);
        userModel.setId(userName);
        userModel.setSalt(userService.generateSalt());
        userModel.setPassword(SecureUtil.sha1(userPwd + userModel.getSalt()));
        userModel.setSystemUser(1);
        userModel.setParent(UserModel.SYSTEM_ADMIN);
        try {
            BaseServerController.resetInfo(userModel);
            userService.insert(userModel);
        } catch (Exception e) {
            log.error("初始化用户失败", e);
            return new JsonMessage<>(400, "初始化失败:" + e.getMessage());
        }
        //自动登录
        setSessionAttribute(LoginInterceptor.SESSION_NAME, userModel);
        UserLoginDto userLoginDto = userService.getUserJwtId(userModel);
        List<UserWorkspaceModel> bindWorkspaceModels = userService.myWorkspace(userModel);
        userLoginDto.setBindWorkspaceModels(bindWorkspaceModels);
        // 二次验证信息
        JSONObject jsonObject = new JSONObject();
        String tfaKey = TwoFactorAuthUtils.generateTFAKey();
        jsonObject.put("mfaKey", tfaKey);
        jsonObject.put("url", TwoFactorAuthUtils.generateOtpAuthUrl(userName, tfaKey));
        jsonObject.put("tokenData", userLoginDto);
        return JsonMessage.success("初始化成功", jsonObject);
    }

}
