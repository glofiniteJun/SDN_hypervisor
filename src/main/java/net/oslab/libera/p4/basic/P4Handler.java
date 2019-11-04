/*******************************************************************************
 * Libera HyperVisor development based OpenVirteX for SDN 2.0
 *
 *   
 *
 * This is updated by Libera Project team in Korea University
 *
 * Author: Hee-sang Jin (hsjin@os.korea.ac.kr)
 * Date: 2018-10-27
 * 
 * Description:
 * 
 ******************************************************************************/
package net.oslab.libera.p4.basic;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.onrc.openvirtex.services.path.Node;
import net.onrc.openvirtex.services.path.physicalflow.PhysicalFlow;
import net.onrc.openvirtex.services.path.physicalpath.PhysicalPath;
import net.oslab.libera.p4.basic.component.P4FlowRule;
import net.oslab.libera.p4.basic.component.P4Topology;


public class P4Handler {
	private static Logger log = LogManager.getLogger(P4Handler.class.getName());
	private static P4Handler p4handler = new P4Handler();
	private P4FlowRule flowRuleHandler = P4FlowRule.getInstance();
	private P4Topology topologyHandler = new P4Topology();
	
	protected P4Handler(){
	}
	
	public static P4Handler getInstance(){
		if(p4handler == null){
			p4handler = new P4Handler();
		}
		return p4handler;
	};
	
	/****************************************************/
	synchronized public boolean createFlowRule(PhysicalFlow pFlow){
		if(pFlow.buildIsReady() == false){
			return false;
		}
		
		List<Node> nodes = pFlow.getMainPath().getNodeList();
		
		for(Node node: nodes){
			String fileName = "switch_s" + node.getSwitch().getSwitchId().intValue() + ".txt";
			Object jsonObject = flowRuleHandler.createMatchAction(node);
			saveToJson(jsonObject, fileName);
		}
		return true;
	};
	public boolean createTopology(){
		
		return true;
	};
	/****************************************************/

	private static synchronized boolean saveToJson(Object object, String fileName){
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		String pathJson = gson.toJson(object) + "\n";
		try{
			log.info("Path Json: {}", fileName);
			OutputStream output = new FileOutputStream("./" + fileName, false); //False: overwrite
			byte[] by = pathJson.getBytes();
			output.write(by);
			output.close();
		} catch(IOException e) {
			log.info("Path Json: {} - ERROR", fileName);
			e.getStackTrace();
		}
		return true;
	}
		
}
