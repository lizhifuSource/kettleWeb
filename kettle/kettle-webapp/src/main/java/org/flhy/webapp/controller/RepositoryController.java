package org.flhy.webapp.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.flhy.ext.App;
import org.flhy.ext.job.JobEncoder;
import org.flhy.ext.repository.RepositoryCodec;
import org.flhy.ext.trans.TransEncoder;
import org.flhy.ext.utils.JSONArray;
import org.flhy.ext.utils.JSONObject;
import org.flhy.ext.utils.StringEscapeHelper;
import org.flhy.webapp.utils.JsonUtils;
import org.pentaho.di.core.Props;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettlePluginException;
import org.pentaho.di.core.exception.KettleSecurityException;
import org.pentaho.di.core.plugins.PluginInterface;
import org.pentaho.di.core.plugins.PluginRegistry;
import org.pentaho.di.core.plugins.PluginTypeInterface;
import org.pentaho.di.core.plugins.RepositoryPluginType;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.job.JobMeta;
import org.pentaho.di.repository.ObjectId;
import org.pentaho.di.repository.RepositoriesMeta;
import org.pentaho.di.repository.Repository;
import org.pentaho.di.repository.RepositoryDirectoryInterface;
import org.pentaho.di.repository.RepositoryElementMetaInterface;
import org.pentaho.di.repository.RepositoryMeta;
import org.pentaho.di.repository.RepositoryObject;
import org.pentaho.di.repository.RepositoryObjectType;
import org.pentaho.di.repository.StringObjectId;
import org.pentaho.di.repository.kdr.KettleDatabaseRepositoryMeta;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.ui.core.PropsUI;
import org.pentaho.di.ui.repository.kdr.KettleDatabaseRepositoryDialog;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.mxgraph.io.mxCodec;
import com.mxgraph.util.mxUtils;
import com.mxgraph.view.mxGraph;

@Controller
@RequestMapping(value = "/repository")
public class RepositoryController {

	/**
	 * 该方法返回所有的资源库信息
	 * 
	 * @throws KettleException 
	 * @throws IOException 
	 */
	@ResponseBody
	@RequestMapping(method = RequestMethod.POST, value = "/list")
	protected void load() throws KettleException, IOException {
		RepositoriesMeta input = new RepositoriesMeta();
		JSONArray jsonArray = new JSONArray();
		if (input.readData()) {
			for (int i = 0; i < input.nrRepositories(); i++) {
				RepositoryMeta repositoryMeta = input.getRepository(i);
				jsonArray.add(RepositoryCodec.encode(repositoryMeta));
			}
		}

		JsonUtils.response(jsonArray);
	}
	
	/**
	 * 该方法打开资源库中的资源，可能转换或作业
	 * 
	 * @param objectId
	 * @param type
	 * @throws Exception
	 */
	@ResponseBody
	@RequestMapping(method=RequestMethod.POST, value="/open")
	protected void open(@RequestParam String objectId, @RequestParam int type) throws Exception {
		JSONObject jsonObject = new JSONObject();
		    
	    if(type == 0) {	//trans
	    	jsonObject.put("GraphType", "TransGraph");
	    	ObjectId id = new StringObjectId( objectId );
	    	
	    	RepositoryObject repositoryObject = App.getInstance().getRepository().getObjectInformation(id, RepositoryObjectType.TRANSFORMATION);
			TransMeta transMeta = App.getInstance().getRepository().loadTransformation(id, null);
			transMeta.setRepositoryDirectory(repositoryObject.getRepositoryDirectory());
	    	
			mxCodec codec = new mxCodec();
			mxGraph graph = TransEncoder.encode(transMeta);
			String graphXml = mxUtils.getPrettyXml(codec.encode(graph.getModel()));
			
			jsonObject.put("graphXml", StringEscapeHelper.encode(graphXml));
	    } else if(type == 1) { //job
	    	jsonObject.put("GraphType", "JobGraph");
	        
	    	ObjectId id = new StringObjectId( objectId );
	    	JobMeta jobMeta = App.getInstance().getRepository().loadJob(id, null);
	    	
	        mxCodec codec = new mxCodec();
			mxGraph graph = JobEncoder.encode(jobMeta);
			String graphXml = mxUtils.getPrettyXml(codec.encode(graph.getModel()));
			
			jsonObject.put("graphXml", StringEscapeHelper.encode(graphXml));
	    }
	    
	    JsonUtils.response(jsonObject);
	}
	
	/**
	 * 该方法获取所有的仓库类型，目前支持数据库和文件系统类型
	 * @throws IOException 
	 * 
	 */
	@ResponseBody
	@RequestMapping(method = RequestMethod.POST, value = "/types")
	protected void types() throws IOException {
		JSONArray jsonArray = new JSONArray();
		
		PluginRegistry registry = PluginRegistry.getInstance();
	    Class<? extends PluginTypeInterface> pluginType = RepositoryPluginType.class;
	    List<PluginInterface> plugins = registry.getPlugins( pluginType );

	    for ( int i = 0; i < plugins.size(); i++ ) {
	      PluginInterface plugin = plugins.get( i );
	      
	      JSONObject jsonObject = new JSONObject();
	      jsonObject.put("type", plugin.getIds()[0]);
	      jsonObject.put("name", plugin.getName() + " : " + plugin.getDescription());
	      jsonArray.add(jsonObject);
	    }

	    JsonUtils.response(jsonArray);
	}

	/**
	 * 加载制定的资源库信息
	 * 
	 * @param reposityId
	 * @throws IOException 
	 * @throws KettleException 
	 */
	@ResponseBody
	@RequestMapping(method = RequestMethod.GET, value = "/{reposityId}")
	protected void reposity(@PathVariable String reposityId) throws KettleException, IOException {
		RepositoriesMeta input = new RepositoriesMeta();
		if (input.readData()) {
			RepositoryMeta repositoryMeta = input.searchRepository( reposityId );
			if(repositoryMeta != null) {
				JsonUtils.response(RepositoryCodec.encode(repositoryMeta));
			}
		}
	}
	
	/**
	 * 资源库浏览，生成树结构
	 * 
	 * @throws KettleException 
	 * @throws IOException 
	 */
	@ResponseBody
	@RequestMapping(method=RequestMethod.POST, value="/explorer")
	protected void explorer() throws KettleException, IOException {
		JSONArray nodes = new JSONArray();
		
		Repository repository = App.getInstance().getRepository();
		RepositoryDirectoryInterface repositoryDirectory = repository.loadRepositoryDirectoryTree();
		browser(repository, repositoryDirectory.findRoot(), nodes);
		
		JsonUtils.response(nodes);
	}
	
	private void browser(Repository repository, RepositoryDirectoryInterface dir, ArrayList list) throws KettleException {
		HashMap<String, Object> node = new HashMap<String, Object>();
		node.put("id", "directory_" + dir.getObjectId().getId());
		node.put("objectId", dir.getObjectId().getId());
		node.put("text", dir.getName());
		
		ArrayList children = new ArrayList();
		node.put("children", children);
		list.add(node);
		
		List<RepositoryDirectoryInterface> directorys = dir.getChildren();
		for(RepositoryDirectoryInterface child : directorys)
			browser(repository, child, children);
		
		List<RepositoryElementMetaInterface> elements = repository.getTransformationObjects(dir.getObjectId(), false);
		if(elements != null) {
			for(RepositoryElementMetaInterface e : elements) {
				HashMap<String, Object> leaf = new HashMap<String, Object>();
				leaf.put("id", "transaction_" + e.getObjectId().getId());
				leaf.put("objectId", e.getObjectId().getId());
				leaf.put("text", e.getName());
				leaf.put("iconCls", "trans_tree");
				leaf.put("leaf", true);
				children.add(leaf);
			}
		}
		
		elements = repository.getJobObjects(dir.getObjectId(), false);
		if(elements != null) {
			for(RepositoryElementMetaInterface e : elements) {
				HashMap<String, Object> leaf = new HashMap<String, Object>();
				leaf.put("id", "job_" + e.getObjectId().getId());
				leaf.put("objectId", e.getObjectId().getId());
				leaf.put("text", e.getName());
				leaf.put("iconCls", "job_tree");
				leaf.put("leaf", true);
				children.add(leaf);
			}
		}
	}
	
	/**
	 * 新增或修改资源库
	 * 
	 * @param reposityInfo
	 * @param add 操作类型,true - 新建
	 * @throws IOException 
	 * @throws KettleException 
	 */
	@ResponseBody
	@RequestMapping(method = RequestMethod.POST, value = "/add")
	protected void add(@RequestParam String reposityInfo, @RequestParam boolean add) throws IOException, KettleException {
		JSONObject jsonObject = JSONObject.fromObject(reposityInfo);
		
		RepositoryMeta repositoryMeta = RepositoryCodec.decode(jsonObject);
		Repository reposity = PluginRegistry.getInstance().loadClass( RepositoryPluginType.class,  repositoryMeta, Repository.class );
		reposity.init( repositoryMeta );
	        
		if ( repositoryMeta instanceof KettleDatabaseRepositoryMeta && !StringUtils.hasText(jsonObject.optJSONObject("extraOptions").optString("database")) ) {
			JsonUtils.fail(BaseMessages.getString( KettleDatabaseRepositoryDialog.class, "RepositoryDialog.Dialog.Error.Title" ), 
					BaseMessages.getString( KettleDatabaseRepositoryDialog.class, "RepositoryDialog.Dialog.ErrorNoConnection.Message" ));
			return;
		} else if(!StringUtils.hasText(repositoryMeta.getName())) {
			JsonUtils.fail(BaseMessages.getString( KettleDatabaseRepositoryDialog.class, "RepositoryDialog.Dialog.Error.Title" ), 
					BaseMessages.getString( KettleDatabaseRepositoryDialog.class, "RepositoryDialog.Dialog.ErrorNoId.Message" ));
			return;
		} else if(!StringUtils.hasText(repositoryMeta.getDescription())) {
			JsonUtils.fail(BaseMessages.getString( KettleDatabaseRepositoryDialog.class, "RepositoryDialog.Dialog.Error.Title" ), 
					BaseMessages.getString( KettleDatabaseRepositoryDialog.class, "RepositoryDialog.Dialog.ErrorNoName.Message" ));
			return;
		} else {
			RepositoriesMeta input = new RepositoriesMeta();
			input.readData();
			
			if(add) {
				if(input.searchRepository(repositoryMeta.getName()) != null) {
					JsonUtils.fail(BaseMessages.getString( KettleDatabaseRepositoryDialog.class, "RepositoryDialog.Dialog.Error.Title" ), 
							BaseMessages.getString( KettleDatabaseRepositoryDialog.class, "RepositoryDialog.Dialog.ErrorIdExist.Message", repositoryMeta.getName()));
					return;
				} else {
					input.addRepository(repositoryMeta);
					input.writeData();
				}
			} else {
				RepositoryMeta previous = input.searchRepository(repositoryMeta.getName());
				input.removeRepository(input.indexOfRepository(previous));
				input.addRepository(repositoryMeta);
				input.writeData();
			}
		}
		
		JsonUtils.success("操作成功！");
	}
	
	/**
	 * 删除资源库
	 * 
	 * @param repositoryName
	 * @throws KettleException 
	 * @throws IOException 
	 */
	@ResponseBody
	@RequestMapping(method = RequestMethod.POST, value = "/remove")
	protected void remove(@RequestParam String repositoryName) throws KettleException, IOException {
		RepositoriesMeta input = new RepositoriesMeta();
		input.readData();
		
		RepositoryMeta previous = input.searchRepository(repositoryName);
		input.removeRepository(input.indexOfRepository(previous));
		input.writeData();
		
		JsonUtils.success("操作成功！");
	}
	
	/**
	 * 登录资源库
	 * 
	 * @param loginInfo
	 * @throws IOException 
	 * @throws KettleException 
	 * @throws KettleSecurityException 
	 * @throws KettlePluginException 
	 */
	@ResponseBody
	@RequestMapping(method = RequestMethod.POST, value = "/login")
	protected void login(@RequestParam String loginInfo) throws IOException, KettlePluginException, KettleSecurityException, KettleException {
		JSONObject jsonObject = JSONObject.fromObject(loginInfo);
		
		RepositoriesMeta input = new RepositoriesMeta();
		if (input.readData()) {
			RepositoryMeta repositoryMeta = input.searchRepository( jsonObject.optString("reposityId") );
			if(repositoryMeta != null) {
				Repository repository = PluginRegistry.getInstance().loadClass(RepositoryPluginType.class, repositoryMeta.getId(), Repository.class );
			    repository.init( repositoryMeta );
			    repository.connect( jsonObject.optString("username"), jsonObject.optString("password") );
			    
			    Props.getInstance().setLastRepository( repositoryMeta.getName() );
			    Props.getInstance().setLastRepositoryLogin( jsonObject.optString("username") );
			    Props.getInstance().setProperty( PropsUI.STRING_START_SHOW_REPOSITORIES, jsonObject.optBoolean("atStartupShown") ? "Y" : "N");
			    
			    Props.getInstance().saveProps();
			    
			    App.getInstance().selectRepository(repository);
			}
		}
		
		JsonUtils.success("登录成功！");
	}
	
	
	/**
	 * 断开资源库
	 * 
	 * @param loginInfo
	 * @throws IOException 
	 */
	@ResponseBody
	@RequestMapping(method = RequestMethod.POST, value = "/logout")
	protected void logout() throws IOException {
		App.getInstance().selectRepository(App.getInstance().getDefaultRepository());
		JsonUtils.success("操作成功！");
	}
	
}