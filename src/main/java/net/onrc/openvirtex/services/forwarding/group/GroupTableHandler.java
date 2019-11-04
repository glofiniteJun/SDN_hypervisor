/*******************************************************************************
 * Libera HyperVisor development based OpenVirteX for SDN 2.0
 *
 *   
 *
 * This is updated by Libera Project team in Korea University
 *
 * Author: Hee-sang Jin (hsjin@os.korea.ac.kr)
 * Date: 2018-05-13
 * 
 * Description:
 * This class is my first contribution...
 * But, It is not used anymore...
 ******************************************************************************/

package net.onrc.openvirtex.services.forwarding.group;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.projectfloodlight.openflow.protocol.OFBucket;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFGroupMod;
import org.projectfloodlight.openflow.protocol.OFGroupType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionGroup;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;

import net.onrc.openvirtex.services.path.Node;
import net.onrc.openvirtex.services.path.physicalpath.PhysicalPath;

public class GroupTableHandler {
	private static Logger log = LogManager.getLogger(GroupTableHandler.class.getName());
    private static OFFactory factory;
    
	public GroupTableHandler(){
		factory = OFFactories.getFactory(OFVersion.OF_13);
	}
		
	public void makeGroupMod(PhysicalPath pPath, Node node, List<OFAction> actions){
		//* GroupMod =====
		int bucketWeight = 5;
        ArrayList<OFBucket> buckets = new ArrayList<OFBucket>();
        buckets.add(factory.buildBucket()
            .setActions(actions)
            .setWatchGroup(OFGroup.ANY)
            .setWatchPort(OFPort.ANY)
            .setWeight(bucketWeight)
            .build());
        
        OFGroupMod mplsGroupAdd = factory.buildGroupAdd()
        	    .setGroup(OFGroup.of(getMplsValue(pPath)))
        	    .setGroupType(OFGroupType.SELECT)
        	    .setBuckets(buckets)
        	    .build();
        
        OFGroupMod mplsGroupMod = factory.buildGroupModify()
        	    .setGroup(OFGroup.of(getMplsValue(pPath)))
        	    .setGroupType(OFGroupType.SELECT)
        	    .setBuckets(buckets)
        	    .build();
        node.setMplsGroupMod(mplsGroupMod.createBuilder().build());
        node.setMplsGroupAdd(mplsGroupAdd.createBuilder().build());
        //===== GroupMod */
	}
	
	
	public List<OFAction> actionsToGroup(PhysicalPath pPath){
		//* FlowMod Action: to group(flowId)
        List<OFAction> actions = new ArrayList<OFAction>();
        OFActionGroup actionGroup = factory.actions().buildGroup()
        		.setGroup(OFGroup.of(getMplsValue(pPath)))
                .build();
        actions.add(0, actionGroup);
        return actions;
	}
	
	public void addActionToGroup(PhysicalPath pPath, Node node, List<OFAction> actions){
		int bucketWeight = 5;
		//* GroupMod =====
		List<OFBucket> buckets = new ArrayList<OFBucket>();
        buckets.addAll((ArrayList<OFBucket>) node.getMplsGroupMod().getBuckets());
        buckets.add(factory.buildBucket()
            .setActions(actions)
            .setWatchGroup(OFGroup.ANY)
            .setWatchPort(OFPort.ANY)
            .setWeight(bucketWeight)
            .build());
        
        OFGroupMod mplsGroupMod = factory.buildGroupModify()
        	    .setGroup(OFGroup.of(getMplsValue(pPath)))
        	    .setGroupType(OFGroupType.SELECT)
        	    .setBuckets(buckets)
        	    .build();
        node.setMplsGroupMod(mplsGroupMod.createBuilder().build());
        node.setMplsGroupAdd(mplsGroupMod.createBuilder().build());
        //===== GroupMod */
	}
	
	public void removeSubActionFromGroup(PhysicalPath pPath, Node node){
		List<OFBucket> buckets = new ArrayList<OFBucket>();
        
    	buckets.add((node.getMplsGroupMod().getBuckets()).get(0)); // get only Primary
        OFGroupMod mplsGroupMod = factory.buildGroupModify()
        	    .setGroup(OFGroup.of(getMplsValue(pPath)))
        	    .setGroupType(OFGroupType.SELECT)
        	    .setBuckets(buckets)
        	    .build();
        node.setMplsGroupMod(mplsGroupMod.createBuilder().build());
        node.setMplsGroupAdd(mplsGroupMod.createBuilder().build());
	}
	
	private int getMplsValue(PhysicalPath pPath){
		int mplsValue = 0;
		if(pPath.isPrimaryPath()){
			mplsValue = pPath.getMplsLabel().getLabelValue();
		}
		else{
			mplsValue = pPath.getPrimaryPath().getMplsLabel().getLabelValue()+1;
		}
		
		return mplsValue;
	}
}
