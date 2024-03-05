/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package org.dromara.jpom.controller.monitor;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.EnumUtil;
import cn.hutool.core.util.StrUtil;
import cn.keepbx.jpom.IJsonMessage;
import cn.keepbx.jpom.model.JsonMessage;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.dromara.jpom.common.BaseServerController;
import org.dromara.jpom.common.validator.ValidatorItem;
import org.dromara.jpom.common.validator.ValidatorRule;
import org.dromara.jpom.model.PageResultDto;
import org.dromara.jpom.model.data.MonitorUserOptModel;
import org.dromara.jpom.permission.ClassFeature;
import org.dromara.jpom.permission.Feature;
import org.dromara.jpom.permission.MethodFeature;
import org.dromara.jpom.service.monitor.MonitorUserOptService;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 监控用户操作
 *
 * @author bwcx_jzy
 * @since 2020/08/06
 */
@RestController
@RequestMapping(value = "/monitor_user_opt")
@Feature(cls = ClassFeature.OPT_MONITOR)
public class MonitorUserOptListController extends BaseServerController {

    private final MonitorUserOptService monitorUserOptService;

    public MonitorUserOptListController(MonitorUserOptService monitorUserOptService) {
        this.monitorUserOptService = monitorUserOptService;
    }


    @RequestMapping(value = "list_data", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @Feature(method = MethodFeature.LIST)
    public IJsonMessage<PageResultDto<MonitorUserOptModel>> getMonitorList(HttpServletRequest request) {
        PageResultDto<MonitorUserOptModel> pageResultDto = monitorUserOptService.listPage(request);
        return JsonMessage.success("", pageResultDto);
    }

    /**
     * 操作监控类型列表
     *
     * @return json
     */
    @RequestMapping(value = "type_data", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @Feature(method = MethodFeature.LIST)
    public IJsonMessage<JSONObject> getOperateTypeList() {
        JSONObject jsonObject = new JSONObject();
        //
        List<JSONObject> classFeatureList = Arrays.stream(ClassFeature.values())
            .filter(classFeature -> classFeature != ClassFeature.NULL)
            .map(classFeature -> {
                JSONObject jsonObject1 = new JSONObject();
                jsonObject1.put("title", classFeature.getName());
                jsonObject1.put("value", classFeature.name());
                return jsonObject1;
            })
            .collect(Collectors.toList());
        jsonObject.put("classFeature", classFeatureList);
        //
        List<JSONObject> methodFeatureList = Arrays.stream(MethodFeature.values())
            .filter(methodFeature -> methodFeature != MethodFeature.NULL && methodFeature != MethodFeature.LIST)
            .map(classFeature -> {
                JSONObject jsonObject1 = new JSONObject();
                jsonObject1.put("title", classFeature.getName());
                jsonObject1.put("value", classFeature.name());
                return jsonObject1;
            })
            .collect(Collectors.toList());
        jsonObject.put("methodFeature", methodFeatureList);

        return JsonMessage.success("success", jsonObject);
    }

    /**
     * 删除列表
     *
     * @param id id
     * @return json
     */
    @RequestMapping(value = "delete", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @Feature(method = MethodFeature.DEL)
    public IJsonMessage<Object> deleteMonitor(@ValidatorItem(value = ValidatorRule.NOT_BLANK, msg = "删除失败") String id, HttpServletRequest request) {
        //
        monitorUserOptService.delByKey(id, request);
        return JsonMessage.success("删除成功");
    }


    /**
     * 增加或修改监控
     *
     * @param id         id
     * @param name       name
     * @param notifyUser user
     * @return json
     */
    @RequestMapping(value = "update", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @Feature(method = MethodFeature.EDIT)
    public IJsonMessage<Object> updateMonitor(String id,
                                              @ValidatorItem(value = ValidatorRule.NOT_BLANK, msg = "监控名称不能为空") String name,
                                              String notifyUser,
                                              String monitorUser,
                                              String monitorOpt,
                                              String monitorFeature) {

        String status = getParameter("status");

        JSONArray jsonArray = JSONArray.parseArray(notifyUser);
        List<String> notifyUsers = jsonArray.toJavaList(String.class)
            .stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        Assert.notEmpty(notifyUsers, "请选择报警联系人");


        JSONArray monitorUserArray = JSONArray.parseArray(monitorUser);
        List<String> monitorUserArrays = monitorUserArray.toJavaList(String.class)
            .stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        Assert.notEmpty(monitorUserArrays, "请选择监控人员");


        JSONArray monitorOptArray = JSONArray.parseArray(monitorOpt);
        List<MethodFeature> monitorOptArrays = monitorOptArray
            .stream()
            .map(o -> EnumUtil.fromString(MethodFeature.class, StrUtil.toString(o), null))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        Assert.notEmpty(monitorOptArrays, "请选择监控的操作");

        JSONArray monitorFeatureArray = JSONArray.parseArray(monitorFeature);
        List<ClassFeature> monitorFeatureArrays = monitorFeatureArray
            .stream()
            .map(o -> EnumUtil.fromString(ClassFeature.class, StrUtil.toString(o), null))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        Assert.notEmpty(monitorFeatureArrays, "请选择监控的功能");


        boolean start = "on".equalsIgnoreCase(status);
        MonitorUserOptModel monitorModel = monitorUserOptService.getByKey(id);
        if (monitorModel == null) {
            monitorModel = new MonitorUserOptModel();
        }
        monitorModel.monitorUser(monitorUserArrays);
        monitorModel.setStatus(start);
        monitorModel.monitorOpt(monitorOptArrays);
        monitorModel.monitorFeature(monitorFeatureArrays);
        monitorModel.notifyUser(notifyUsers);
        monitorModel.setName(name);

        if (StrUtil.isEmpty(id)) {
            //添加监控
            monitorUserOptService.insert(monitorModel);
            return JsonMessage.success("添加成功");
        }
        monitorUserOptService.updateById(monitorModel);
        return JsonMessage.success("修改成功");
    }

    /**
     * 开启或关闭监控
     *
     * @param id     id
     * @param status 状态
     * @return json
     */
    @RequestMapping(value = "changeStatus", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @Feature(method = MethodFeature.EDIT)
    public IJsonMessage<Object> changeStatus(@ValidatorItem(value = ValidatorRule.NOT_BLANK, msg = "id不能为空") String id,
                                             String status) {
        MonitorUserOptModel monitorModel = monitorUserOptService.getByKey(id);
        Assert.notNull(monitorModel, "不存在监控项啦");

        boolean bStatus = Convert.toBool(status, false);
        monitorModel.setStatus(bStatus);
        monitorUserOptService.updateById(monitorModel);
        return JsonMessage.success("修改成功");
    }


}
