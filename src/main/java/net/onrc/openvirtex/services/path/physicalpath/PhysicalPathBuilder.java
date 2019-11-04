/*******************************************************************************
 * Libera HyperVisor development based OpenVirteX for SDN 2.0
 *
 *   for Virtual Machine Migration
 *
 * This is updated by Libera Project team in Korea University
 *
 * Author: Seong-Mun Kim (bebecry@gmail.com)
 * Date: 2017-06-07
 ******************************************************************************/
package net.onrc.openvirtex.services.path.physicalpath;

import net.onrc.openvirtex.elements.datapath.PhysicalSwitch;
import net.onrc.openvirtex.elements.host.Host;
import net.onrc.openvirtex.elements.link.OVXLink;
import net.onrc.openvirtex.elements.link.PhysicalLink;
import net.onrc.openvirtex.elements.network.OVXNetwork;
import net.onrc.openvirtex.elements.network.PhysicalNetwork;
import net.onrc.openvirtex.elements.port.OVXPort;
import net.onrc.openvirtex.elements.port.PhysicalPort;
import net.onrc.openvirtex.exceptions.IndexOutOfBoundException;
import net.onrc.openvirtex.exceptions.LinkMappingException;
import net.onrc.openvirtex.exceptions.NetworkMappingException;
import net.onrc.openvirtex.protocol.OVXMatch;
import net.onrc.openvirtex.routing.ShortestPath;
import net.onrc.openvirtex.services.forwarding.mpls.MplsForwarding;
import net.onrc.openvirtex.services.forwarding.mpls.MplsLabel;
import net.onrc.openvirtex.services.path.PathUtil;
import net.onrc.openvirtex.services.path.physicalflow.PhysicalFlow;
import net.onrc.openvirtex.services.path.physicalpath.PhysicalPath.PathType;
import net.onrc.openvirtex.services.path.SwitchType;
import net.onrc.openvirtex.services.path.virtualpath.VirtualPath;
import net.onrc.openvirtex.services.path.virtualpath.VirtualPathBuilder;
import net.onrc.openvirtex.util.BitSetIndex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.projectfloodlight.openflow.protocol.OFActionType;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFGroupMod;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionSetField;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;
import net.onrc.openvirtex.services.path.Node;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class PhysicalPathBuilder {
    private static PhysicalPathBuilder instance;
    private static Logger log = LogManager.getLogger(PhysicalPathBuilder.class.getName());
    private static OFFactory factory;

    private static ConcurrentHashMap<Integer,PhysicalPath> PathIDphysicalPathMap;
    private static ConcurrentHashMap<Integer,PhysicalFlow> FlowIDphysicalFlowMap;

    private final BitSetIndex pathCounter;
    

    private PhysicalPathBuilder() {
        PathIDphysicalPathMap = new ConcurrentHashMap<>();
        FlowIDphysicalFlowMap = new ConcurrentHashMap<>();
        this.pathCounter = new BitSetIndex(BitSetIndex.IndexType.PATH_ID);
        factory = OFFactories.getFactory(OFVersion.OF_13);
    }

    
    public synchronized static PhysicalPathBuilder getInstance() {
        if (PhysicalPathBuilder.instance == null) {
            log.info("Starting PhysicalPathBuilder");

            PhysicalPathBuilder.instance = new PhysicalPathBuilder();
        }
        return PhysicalPathBuilder.instance;
    }

    public synchronized  void addPhysicalPath(PhysicalPath pPath) {
        PathIDphysicalPathMap.put(pPath.getPathID(), pPath);
    }
    

    public synchronized PhysicalFlow buildPhysicalPath(VirtualPath vPath, OFFlowMod oFlowMod,
                                                    OFFlowMod mFlowMod, SwitchType type,
                                                    PhysicalSwitch psw) throws IndexOutOfBoundException, LinkMappingException {
        
    	// Create PhysicalFlow from srcHost to dstHost
    	int flowId = vPath.getFlowID();
    	PhysicalFlow pFlow = FlowIDphysicalFlowMap.get(flowId);
    	if(pFlow == null){
    		pFlow = new PhysicalFlow(flowId);
    		FlowIDphysicalFlowMap.put(flowId, pFlow);
    		log.info("pFlow {} is building", pFlow.getFlowId());
    	}
    	
    	// Create PhysicalPath main path
    	int pathID = PathUtil.getInstance().makePathID(vPath.getTenantID(), vPath.getFlowID());
        PhysicalPath pPath = PathIDphysicalPathMap.get(pathID);
        if(pPath == null){
        	// Create main path
            pPath = new PhysicalPath(vPath.getFlowID(), vPath.getTenantID(), pathID, vPath, PathType.MAIN);
            vPath.setPhysicalPath(pPath);
            
            // Set the created path of the flow as a main path
            pFlow.setMainPath(pPath);
            
            PathIDphysicalPathMap.put(pathID, pPath);
            log.info("{} is building", pPath.getPathInfo());
        }
        pPath.buildPhysicalPath(vPath, oFlowMod, mFlowMod, type, psw);
        return pFlow;
    }
    
    // Create a Sub path or Recovery path according pathType
    synchronized public PhysicalPath createSubPhysicalPath(VirtualPath vPath, PhysicalPath oripPath, PathType pathType) throws IndexOutOfBoundException{
    	// Create PhysicalFlow
    	int flowId = vPath.getFlowID();
    	PhysicalFlow pFlow = FlowIDphysicalFlowMap.get(flowId);
    	if(pFlow == null){
    		// The flow must already be set.
    		log.info("pFlow is NULL. Something is wrong");
    	}
    	
    	// Make subPathID whose flow_id is equal to primary_path`s flow_id 
    	int newPathID = PathUtil.getInstance().makePathIDindex(vPath.getTenantID(), vPath.getFlowID(), pathType);
    	// Make subPath Object using subPathID
    	PhysicalPath newpPath = PathIDphysicalPathMap.get(newPathID);
        if(newpPath == null){
        	// Create a sub path
        	newpPath = new PhysicalPath(vPath.getFlowID(), vPath.getTenantID(), newPathID, vPath, pathType);
        	newpPath.setPrimaryPath(oripPath);
            vPath.addPhysicalPath(newpPath);

            // Add the new path of the flow as a Sub path or Recovery path
            if(pathType == PathType.RECOVERY){
            	pFlow.setRecoveryPath(newpPath);
            }
            if(pathType == PathType.SUB){
            	pFlow.addSubPath(newpPath);
            }
            
            PathIDphysicalPathMap.put(newPathID, newpPath);
            log.info("{} is building", newpPath.getPathInfo());
        }
        
        newpPath.buildSubPhysicalPath();
        return newpPath;
    }

    
    
    public synchronized OFFlowMod removePhysicalPath(OFFlowMod oriFlowMod, OVXMatch ovxMatch) {
        int pathID = PathUtil.getInstance().makePathID(ovxMatch.getTenantId(), ovxMatch.getFlowId());
        PhysicalPath pPath = PathIDphysicalPathMap.get(pathID);

        if(pPath == null){
            log.info("PhysicalPath ID [{}] does not exist", pathID);
            return null;
        }else{
            //log.info("PhysicalPath ID [{}] exists", ovxMatch.getFlowId());

            VirtualPath vPath = VirtualPathBuilder.getInstance().getVirtualPath(pathID);


            if(vPath == null) {
                PathIDphysicalPathMap.remove(pathID);
                log.info("PhysicalPath ID [{}] is removed", pathID);
            }

            return pPath.getModifiedFlowMod(oriFlowMod);        //path 지우는 것도 따져봐야함
        }
    }

    
    
    public PhysicalPath getPhysicalPath(Integer pathId) {
        return PathIDphysicalPathMap.get(pathId);
    }
}
