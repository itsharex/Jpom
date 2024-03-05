/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package org.dromara.jpom.controller.script;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Opt;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.keepbx.jpom.IJsonMessage;
import cn.keepbx.jpom.model.JsonMessage;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.dromara.jpom.common.BaseAgentController;
import org.dromara.jpom.common.Const;
import org.dromara.jpom.common.validator.ValidatorItem;
import org.dromara.jpom.common.validator.ValidatorRule;
import org.dromara.jpom.model.data.NodeScriptExecLogModel;
import org.dromara.jpom.model.data.NodeScriptModel;
import org.dromara.jpom.script.NodeScriptProcessBuilder;
import org.dromara.jpom.service.script.NodeScriptExecLogServer;
import org.dromara.jpom.service.script.NodeScriptServer;
import org.dromara.jpom.util.CommandUtil;
import org.dromara.jpom.util.FileUtils;
import org.dromara.jpom.util.StringUtil;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 脚本管理
 *
 * @author bwcx_jzy
 * @since 2019/4/24
 */
@RestController
@RequestMapping(value = "/script")
public class ScriptController extends BaseAgentController {

    private final NodeScriptServer nodeScriptServer;
    private final NodeScriptExecLogServer nodeScriptExecLogServer;

    public ScriptController(NodeScriptServer nodeScriptServer,
                            NodeScriptExecLogServer nodeScriptExecLogServer) {
        this.nodeScriptServer = nodeScriptServer;
        this.nodeScriptExecLogServer = nodeScriptExecLogServer;
    }

    @RequestMapping(value = "list.json", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public IJsonMessage<List<NodeScriptModel>> list() {
        return JsonMessage.success("", nodeScriptServer.list());
    }

    @RequestMapping(value = "item.json", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public IJsonMessage<NodeScriptModel> item(String id) {
        return JsonMessage.success("", nodeScriptServer.getItem(id));
    }

    @RequestMapping(value = "save.json", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public IJsonMessage<Object> save(NodeScriptModel nodeScriptModel, String type, String global, String nodeId) {
        Assert.notNull(nodeScriptModel, "没有数据");
        Assert.hasText(nodeScriptModel.getContext(), "内容为空");
        //
        String autoExecCron = nodeScriptModel.getAutoExecCron();
        autoExecCron = StringUtil.checkCron(autoExecCron, s -> s);
        //
        boolean globalBool = Convert.toBool(global, false);
        if (globalBool) {
            nodeScriptModel.setWorkspaceId(Const.WORKSPACE_GLOBAL);
        } else {
            nodeScriptModel.setWorkspaceId(getWorkspaceId());
        }
        //
        nodeScriptModel.setContext(nodeScriptModel.getContext());
        NodeScriptModel eModel = nodeScriptServer.getItem(nodeScriptModel.getId());
        boolean needCreate = false;
        if ("add".equalsIgnoreCase(type)) {
            Assert.isNull(eModel, "id已经存在啦");

            nodeScriptModel.setId(IdUtil.fastSimpleUUID());
            nodeScriptModel.setNodeId(nodeId);
            nodeScriptServer.addItem(nodeScriptModel);
            return JsonMessage.success("添加成功");
        } else if ("sync".equalsIgnoreCase(type)) {
            // 同步脚本
            if (eModel == null) {
                eModel = new NodeScriptModel();
                eModel.setId(nodeScriptModel.getId());
                eModel.setNodeId(nodeId);
                needCreate = true;
            } else {
                if (!eModel.global() && nodeScriptModel.global()) {
                    // 修改绑定的节点id
                    eModel.setNodeId(nodeId);
                }
            }
            eModel.setScriptType("server-sync");
            eModel.setWorkspaceId(nodeScriptModel.getWorkspaceId());
        }
        Assert.notNull(eModel, "对应数据不存在");
        eModel.setName(nodeScriptModel.getName());
        eModel.setAutoExecCron(autoExecCron);
        eModel.setDescription(nodeScriptModel.getDescription());
        eModel.setContext(nodeScriptModel.getContext());
        eModel.setDefArgs(nodeScriptModel.getDefArgs());
        if (needCreate) {
            nodeScriptServer.addItem(eModel);
        } else {
            nodeScriptServer.updateItem(eModel);
        }
        return JsonMessage.success("修改成功");
    }

    @RequestMapping(value = "del.json", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public IJsonMessage<Object> del(String id) {
        nodeScriptServer.deleteItem(id);
        return JsonMessage.success("删除成功");
    }

    /**
     * 获取的日志
     *
     * @param id        id
     * @param executeId 执行ID
     * @param line      需要获取的行号
     * @return json
     */
    @RequestMapping(value = "log", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public IJsonMessage<JSONObject> getNowLog(@ValidatorItem() String id,
                                              @ValidatorItem() String executeId,
                                              @ValidatorItem(value = ValidatorRule.POSITIVE_INTEGER, msg = "line") int line) {
        NodeScriptModel item = nodeScriptServer.getItem(id);
        Assert.notNull(item, "没有对应数据");
        File logFile = item.logFile(executeId);
        Assert.state(FileUtil.isFile(logFile), "日志文件错误");

        JSONObject data = FileUtils.readLogFile(logFile, line);
        // 运行中
        data.put("run", NodeScriptProcessBuilder.isRun(executeId));
        return JsonMessage.success("ok", data);
    }

    /**
     * 删除日志
     *
     * @param id        id
     * @param executeId 执行ID
     * @return json
     */
    @RequestMapping(value = "del_log", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public IJsonMessage<Object> delLog(@ValidatorItem() String id,
                                       @ValidatorItem() String executeId) {
        NodeScriptModel item = nodeScriptServer.getItem(id);
        if (item == null) {
            return JsonMessage.success("对应的脚本模版已经不存在拉");
        }
        Assert.notNull(item, "没有对应数据");
        File logFile = item.logFile(executeId);
        boolean fastDel = CommandUtil.systemFastDel(logFile);
        Assert.state(!fastDel, "删除日志文件失败");
        return JsonMessage.success("删除成功");
    }

    /**
     * 执行
     *
     * @param id     ID
     * @param args   执行参数
     * @param params 环境变量参数
     * @return json
     */
    @RequestMapping(value = "exec", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public IJsonMessage<String> exec(@ValidatorItem() String id, String args, String params) {
        NodeScriptModel item = nodeScriptServer.getItem(id);
        Assert.notNull(item, "对应脚本已经不存在啦");
        String nowUserName = getNowUserName();

        Map<String, String> paramMap = Opt.ofBlankAble(params)
            .map(JSONObject::parseObject)
            .map(jsonObject -> {
                Map<String, String> paramMap1 = new HashMap<>(10);
                for (Map.Entry<String, Object> entry : jsonObject.entrySet()) {
                    String key = StrUtil.format("trigger_{}", entry.getKey());
                    key = StrUtil.toUnderlineCase(key);
                    paramMap1.put(key, StrUtil.toString(entry.getValue()));
                }
                return paramMap1;
            })
            .orElse(null);
        //

        String execute = nodeScriptServer.execute(item, 2, nowUserName, null, args, paramMap);
        return JsonMessage.success("开始执行", execute);
    }

    /**
     * 同步定时执行日志
     *
     * @param pullCount 领取个数
     * @return json
     */
    @RequestMapping(value = "pull_exec_log", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public IJsonMessage<List<NodeScriptExecLogModel>> pullExecLog(@ValidatorItem int pullCount) {
        Assert.state(pullCount > 0, "pull count error");
        List<NodeScriptExecLogModel> list = nodeScriptExecLogServer.list();
        list = CollUtil.sub(list, 0, pullCount);
        if (list == null) {
            return JsonMessage.success("", Collections.emptyList());
        }
        return JsonMessage.success("", list);
    }

    /**
     * 删除定时执行日志
     *
     * @param jsonObject 拉起参数
     * @return json
     */
    @RequestMapping(value = "del_exec_log", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public IJsonMessage<Object> delExecLog(@RequestBody JSONObject jsonObject) {
        JSONArray ids = jsonObject.getJSONArray("ids");
        if (ids != null) {
            for (Object id : ids) {
                String idStr = (String) id;
                nodeScriptExecLogServer.deleteItem(idStr);
            }
        }
        return JsonMessage.success("删除成功");
    }

    @RequestMapping(value = "change-workspace-id", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public IJsonMessage<Object> changeWorkspaceId(@ValidatorItem() String id, String newWorkspaceId, String newNodeId) {
        Assert.hasText(newWorkspaceId, "请选择要修改的工作空间");
        Assert.hasText(newWorkspaceId, "请选择要修改的节");
        NodeScriptModel item = nodeScriptServer.getItem(id);
        Assert.notNull(item, "找不到对应的脚本信息");
        //
        NodeScriptModel update = new NodeScriptModel();
        update.setNodeId(newNodeId);
        update.setWorkspaceId(newWorkspaceId);
        nodeScriptServer.updateById(update, item.getId());
        return JsonMessage.success("修改成功");
    }
}
