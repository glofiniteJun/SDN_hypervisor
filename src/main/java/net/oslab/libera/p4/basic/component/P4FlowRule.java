package net.oslab.libera.p4.basic.component;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.projectfloodlight.openflow.protocol.OFActionType;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.EthType;

import net.onrc.openvirtex.services.path.Node;
import net.onrc.openvirtex.services.path.physicalpath.PhysicalPath;


public class P4FlowRule{
	private static Logger log = LogManager.getLogger(P4FlowRule.class.getName());
	private static P4FlowRule p4FlowRuleHandler = new P4FlowRule();
	private static ConcurrentHashMap<Integer, FlowRuleStructure> SwitchIDFlowRuleMap = new ConcurrentHashMap<>();
	protected P4FlowRule(){
	}
	
	public static P4FlowRule getInstance(){
		if(p4FlowRuleHandler == null){
			p4FlowRuleHandler = new P4FlowRule();
		}
		return p4FlowRuleHandler;
	};
	
	synchronized public Object createMatchAction(Node node){
		// Match Field
		
		BasicMatch match = new BasicMatch();
		match.ipv4_srcAddr = node.getMplsFlowMod().getMatch().get(MatchField.IPV4_SRC).toString();
		if(node.getMplsFlowMod().getMatch().get(MatchField.IPV4_DST) != null)
			match.ipv4_dstAddr = node.getMplsFlowMod().getMatch().get(MatchField.IPV4_DST).toString();
	
		
		//List<OFAction> origianlActions = node.getMplsFlowMod().getActions();
		//action.actionName = origianlActions.get(origianlActions.size()-1).getType().toString() // Output Action

		short inportNumber = node.getmFlowMod().getMatch().get(MatchField.IN_PORT).getShortPortNumber();
		short outportNumber = -1;
		for(OFAction act : node.getMplsFlowMod().getActions()) {
            if(act.getType() == OFActionType.OUTPUT) {
            	outportNumber = ((OFActionOutput)act).getPort().getShortPortNumber();
                break;
            }
        }
		//action.basicActionParams.dstAddr = node.getSwitch().getPort(outportNumber).;
		
		BasicMatchAction basicMatchAction = new BasicMatchAction(match, (int)outportNumber);
		int switchID = node.getSwitch().getSwitchId().intValue();
		FlowRuleStructure p4FlowRule;
		if(SwitchIDFlowRuleMap.containsKey(switchID)){
			p4FlowRule = SwitchIDFlowRuleMap.get(switchID);
			p4FlowRule.put(basicMatchAction);
		}
		else{
			p4FlowRule = new FlowRuleStructure(basicMatchAction);
			SwitchIDFlowRuleMap.put(switchID, p4FlowRule);
		}
		
		return p4FlowRule;
	}
}

class BasicActionParams {
	String dstAddr;
	int port;
	{
		dstAddr = "00:00:00:00:01:01";
		port = -1;
	}
}

class BasicMatch {
	String ipv4_srcAddr = "None";
	String ipv4_dstAddr = "None";
	{
	}
}

class MatchAction {
	
}

class BasicMatchAction{
	String table = "MyIngress.ipv4_lpm";
	BasicMatch basicMatch = new BasicMatch();
	String action_name = "MyIngress.ipv4_forward";
	BasicActionParams action_params = new BasicActionParams();
	
	BasicMatchAction(BasicMatch match, int outportNumber){
		this.basicMatch = match;
		this.action_params.port = outportNumber;
	}
	
	public int hashCode(){
		final int prime = 31;
		int result = 1;
		result = prime * result + this.basicMatch.ipv4_dstAddr.hashCode();
		result = prime * result + this.basicMatch.ipv4_srcAddr.hashCode();
		result = prime * result + this.action_name.hashCode();
		result = prime * result + this.action_params.dstAddr.hashCode();
		result = prime * result + this.action_params.port;
		
		return result;
	}
}

//class DefaultMatchAction {
//	String table = "MyIngress.ipv4_lpm";
//	boolean default_action =  true;
//	String action_name = "MyIngress.ipv4_forward";
//	
//}

class FlowRuleStructure {
	String target = "bmv2";
	String p4info = "build/basic.p4info";
	String bmv2_json = "build/basic.json";
	ConcurrentHashMap<Integer, BasicMatchAction> table_entries = new ConcurrentHashMap<>();
	
	FlowRuleStructure(BasicMatchAction matchAction){
		put(matchAction);
	}
	public boolean put(BasicMatchAction matchAction){
		this.table_entries.put(matchAction.hashCode(), matchAction);
		return true;
	}
	
}


