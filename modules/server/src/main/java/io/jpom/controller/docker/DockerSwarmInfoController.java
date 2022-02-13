package io.jpom.controller.docker;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.db.Entity;
import cn.jiangzeyin.common.JsonMessage;
import cn.jiangzeyin.common.validator.ValidatorItem;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.jpom.common.BaseServerController;
import io.jpom.model.PageResultDto;
import io.jpom.model.docker.DockerInfoModel;
import io.jpom.model.docker.DockerSwarmInfoMode;
import io.jpom.plugin.*;
import io.jpom.service.docker.DockerInfoService;
import io.jpom.service.docker.DockerSwarmInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * @author bwcx_jzy
 * @since 2022/2/13
 */
@RestController
@Feature(cls = ClassFeature.DOCKER_SWARM)
@RequestMapping(value = "/docker-swarm")
@Slf4j
public class DockerSwarmInfoController extends BaseServerController {

	private final DockerSwarmInfoService dockerSwarmInfoService;
	private final DockerInfoService dockerInfoService;

	public DockerSwarmInfoController(DockerSwarmInfoService dockerSwarmInfoService,
									 DockerInfoService dockerInfoService) {
		this.dockerSwarmInfoService = dockerSwarmInfoService;
		this.dockerInfoService = dockerInfoService;
	}

	/**
	 * @return json
	 */
	@PostMapping(value = "list", produces = MediaType.APPLICATION_JSON_VALUE)
	@Feature(method = MethodFeature.LIST)
	public String list() {
		// load list with page
		PageResultDto<DockerSwarmInfoMode> resultDto = dockerSwarmInfoService.listPage(getRequest());
		return JsonMessage.getString(200, "", resultDto);
	}

	/**
	 * @return json
	 */
	@GetMapping(value = "list-all", produces = MediaType.APPLICATION_JSON_VALUE)
	@Feature(method = MethodFeature.LIST)
	public String listAll() {
		// load list with all
		List<DockerSwarmInfoMode> swarmInfoModes = dockerSwarmInfoService.listByWorkspace(getRequest());
		return JsonMessage.getString(200, "", swarmInfoModes);
	}

	@PostMapping(value = "init", produces = MediaType.APPLICATION_JSON_VALUE)
	@Feature(method = MethodFeature.EDIT)
	public String init(@ValidatorItem String name, @ValidatorItem String tag, @ValidatorItem String dockerId) throws Exception {
		DockerInfoModel dockerInfoModel1 = dockerInfoService.getByKey(dockerId, getRequest());
		Assert.notNull(dockerInfoModel1, "对应的 docker 不存在");
		IPlugin plugin = PluginFactory.getPlugin(DockerSwarmInfoService.DOCKER_PLUGIN_NAME);
		JSONObject data = (JSONObject) plugin.execute("tryInitializeSwarm", dockerInfoModel1.toParameter());

		String id = data.getString("id");
		this.check(null, id);
		this.updateData(data, name, tag, dockerSwarmInfoService::insert, dockerInfoModel1);

		//
		return JsonMessage.getString(200, "集群创建成功");
	}

	private void updateData(JSONObject data, String name, String tag, Consumer<DockerSwarmInfoMode> consumer, DockerInfoModel dockerInfoModel1) throws Exception {
		Date createdAt = data.getDate("createdAt");
		Date updatedAt = data.getDate("updatedAt");
		String id = data.getString("id");
		//
		Map<String, Object> parameter = dockerInfoModel1.toParameter();
		IPlugin plugin = PluginFactory.getPlugin(DockerInfoService.DOCKER_CHECK_PLUGIN_NAME);
		JSONObject info = plugin.execute("info", parameter, JSONObject.class);
		//
		JSONObject swarm = info.getJSONObject("swarm");
		Assert.notNull(swarm, "集群信息不完整,不能操作");
		String nodeAddr = swarm.getString("nodeAddr");
		Assert.hasText(nodeAddr, "没有节点地址,不能继续操作");
		DockerSwarmInfoMode.DockerSwarmInfoModeBuilder infoModeBuilder = DockerSwarmInfoMode.builder()
				.swarmId(id).swarmCreatedAt(createdAt.getTime()).swarmUpdatedAt(updatedAt.getTime()).dockerId(dockerInfoModel1.getId())
				.tag(tag).name(name).nodeAddr(nodeAddr);
		DockerSwarmInfoMode dockerSwarmInfoMode = infoModeBuilder.build();
		consumer.accept(dockerSwarmInfoMode);
		// 更新 docker id
		DockerInfoModel dockerInfoModel = new DockerInfoModel();
		dockerInfoModel.setSwarmId(id);
		dockerInfoModel.setId(dockerInfoModel1.getId());
		dockerInfoService.update(dockerInfoModel);
	}

	private void check(String id, String swarmId) {
		// 验证重复
		String workspaceId = dockerSwarmInfoService.getCheckUserWorkspace(getRequest());
		Entity entity = Entity.create();
		entity.set("swarmId", swarmId);
		entity.set("workspaceId", workspaceId);
		if (StrUtil.isNotEmpty(id)) {
			entity.set("id", StrUtil.format(" <> {}", id));
		}
		boolean exists = dockerSwarmInfoService.exists(entity);
		Assert.state(!exists, "当前 docker 集群已经存在拉,docker 不能重复绑定包括跨工作空间");
	}

	@PostMapping(value = "edit", produces = MediaType.APPLICATION_JSON_VALUE)
	@Feature(method = MethodFeature.EDIT)
	public String edit(@ValidatorItem String id, @ValidatorItem String name, @ValidatorItem String tag) throws Exception {
		DockerSwarmInfoMode dockerSwarmInfoMode1 = dockerSwarmInfoService.getByKey(id);
		Assert.notNull(dockerSwarmInfoMode1, "对应的集群不存在");
		String dockerId = dockerSwarmInfoMode1.getDockerId();
		DockerInfoModel dockerInfoModel1 = dockerInfoService.getByKey(dockerId, getRequest());
		Assert.notNull(dockerInfoModel1, "对应的 docker 不存在");
		//
		IPlugin plugin = PluginFactory.getPlugin(DockerSwarmInfoService.DOCKER_PLUGIN_NAME);
		JSONObject data = (JSONObject) plugin.execute("inSpectSwarm", dockerInfoModel1.toParameter());
		String swarmId = data.getString("id");

		this.check(id, swarmId);

		this.updateData(data, name, tag, dockerSwarmInfoMode -> {
			dockerSwarmInfoMode.setId(id);
			dockerSwarmInfoService.update(dockerSwarmInfoMode);
		}, dockerInfoModel1);
		//
		return JsonMessage.getString(200, "修改成功");
	}

	private void bindDockerSwarm(DockerInfoModel joinSwarmDocker, JSONObject swarm, String swarmId) {
		//
		String swarmNodeId = swarm.getString("nodeID");
		//
		DockerInfoModel dockerInfoModel = new DockerInfoModel();
		dockerInfoModel.setSwarmId(swarmId);
		dockerInfoModel.setSwarmNodeId(swarmNodeId);
		dockerInfoModel.setId(joinSwarmDocker.getId());
		dockerInfoService.update(dockerInfoModel);
	}

	@PostMapping(value = "join", produces = MediaType.APPLICATION_JSON_VALUE)
	@Feature(method = MethodFeature.EDIT)
	public String join(@ValidatorItem String swarmId,
					   @ValidatorItem String dockerId,
					   @ValidatorItem String remoteAddr,
					   @ValidatorItem String role) throws Exception {
		DockerSwarmInfoMode swarmInfoMode1 = dockerSwarmInfoService.getByKey(swarmId);
		Assert.notNull(swarmInfoMode1, "没有对应的集群");
		DockerInfoModel managerSwarmDocker = dockerInfoService.getByKey(swarmInfoMode1.getDockerId(), getRequest());
		Assert.notNull(managerSwarmDocker, "对应的 docker 不存在");
		DockerInfoModel joinSwarmDocker = dockerInfoService.getByKey(dockerId, getRequest());
		Assert.notNull(joinSwarmDocker, "对应的 docker 不存在:-1");
		JSONObject managerSwarmInfo;
		IPlugin plugin = PluginFactory.getPlugin(DockerSwarmInfoService.DOCKER_PLUGIN_NAME);
		IPlugin checkPlugin = PluginFactory.getPlugin(DockerInfoService.DOCKER_CHECK_PLUGIN_NAME);
		{// 获取集群信息
			managerSwarmInfo = (JSONObject) plugin.execute("inSpectSwarm", managerSwarmDocker.toParameter());
			Assert.notNull(managerSwarmInfo, "集群信息不完整,不能加入改集群");
		}
		{// 检查节点存在的信息
			JSONObject info = checkPlugin.execute("info", joinSwarmDocker.toParameter(), JSONObject.class);
			Assert.notNull(info, "获取 docker 集群信息失败");
			JSONObject swarm = info.getJSONObject("swarm");
			Assert.notNull(swarm, "获取 docker 集群信息失败:-1");
			JSONArray remoteManagers = swarm.getJSONArray("remoteManagers");
			if (ArrayUtil.isNotEmpty(remoteManagers)) {
				Optional<Object> optional = remoteManagers.stream().filter(o -> {
					JSONObject jsonObject = (JSONObject) o;
					String addr = jsonObject.getString("addr");
					List<String> strings = StrUtil.splitTrim(addr, StrUtil.COLON);
					return StrUtil.equals(CollUtil.getFirst(strings), remoteAddr);
				}).findFirst();
				Assert.state(optional.isPresent(), "当前 docker 已经加入到其他集群啦");
				// 绑定数据
				String managerId = managerSwarmInfo.getString("id");
				this.bindDockerSwarm(joinSwarmDocker, swarm, managerId);
				return JsonMessage.getString(200, "集群绑定成功");
			}
		}
		String roleToken;
		{// 准备加入集群
			JSONObject joinTokens = managerSwarmInfo.getJSONObject("joinTokens");
			Assert.notNull(joinTokens, "集群信息不完整,不能加入改集群:-1");
			roleToken = joinTokens.getString(role);
			Assert.hasText(roleToken, "不能已 " + role + " 角色加入集群");
		}
		Map<String, Object> parameter = joinSwarmDocker.toParameter();
		parameter.put("token", roleToken);
		parameter.put("remoteAddrs", remoteAddr);
		plugin.execute("joinSwarm", parameter);
		// 绑定数据
		JSONObject info = checkPlugin.execute("info", joinSwarmDocker.toParameter(), JSONObject.class);
		Assert.notNull(info, "获取 docker 集群信息失败");
		JSONObject swarm = info.getJSONObject("swarm");
		Assert.notNull(swarm, "获取 docker 集群信息失败:-1");
		String managerId = managerSwarmInfo.getString("id");
		this.bindDockerSwarm(joinSwarmDocker, swarm, managerId);
		return JsonMessage.getString(200, "集群创建成功");
	}
}