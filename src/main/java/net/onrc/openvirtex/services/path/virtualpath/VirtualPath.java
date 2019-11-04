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
package net.onrc.openvirtex.services.path.virtualpath;

import net.onrc.openvirtex.elements.datapath.OVXSwitch;
import net.onrc.openvirtex.elements.datapath.PhysicalSwitch;
import net.onrc.openvirtex.elements.datapath.Switch;
import net.onrc.openvirtex.elements.host.Host;
import net.onrc.openvirtex.elements.link.OVXLink;
import net.onrc.openvirtex.elements.network.OVXNetwork;
import net.onrc.openvirtex.exceptions.NetworkMappingException;
import net.onrc.openvirtex.protocol.OVXMatch;
import net.onrc.openvirtex.services.path.Node;
import net.onrc.openvirtex.services.path.Path;
import net.onrc.openvirtex.services.path.SwitchType;
import net.onrc.openvirtex.services.path.physicalpath.PhysicalPath;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.projectfloodlight.openflow.protocol.OFActionType;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;

import java.util.*;

public class VirtualPath extends Path{
    private static Logger log = LogManager.getLogger(VirtualPath.class.getName());

    private Map<OFFlowMod, OVXSwitch> flowModOVXSwitchMap;
    private Map<OVXSwitch, OFFlowMod> ovxSwitchFlowModMap;

    private boolean isRemoved;
    private boolean isMigrated;

    private PhysicalPath pPath;
    private PhysicalPath mPath;
    private Vector<PhysicalPath> pPaths;
    
    private long timestamp;

    public VirtualPath(int flowID, int tenantID, int pathID) {
        super(flowID, tenantID, pathID);
        this.flowModOVXSwitchMap = new HashMap<>();
        this.ovxSwitchFlowModMap = new HashMap<>();
        this.isRemoved = false;
        this.isMigrated = false;

        this.timestamp = 0;
        this.pPath = null;
        this.mPath = null;
        this.pPaths = new Vector<>();
    }

    public void setMigratedPhysicalPath(PhysicalPath mPath) {
        this.mPath = mPath;
    }

    public PhysicalPath getMigratedPhysicalPath() {
        return this.mPath;
    }

    public void setPhysicalPath(PhysicalPath pPath) {
        //this.pPath = pPath;
        this.pPaths.add(pPath);
    }
    
    public void addPhysicalPath(PhysicalPath pPath) {
        this.pPaths.add(pPath);
    }

    public PhysicalPath getPhysicalPath() {
        //return this.pPath;
    	return this.pPaths.get(0);
    }

    public void setTimestamp(long t) {
        this.timestamp = t;
    }

    public long getTimestamp() {
        return this.timestamp;
    }

    public void setMigrated(boolean flag) {
        this.isMigrated = flag;
    }

    public boolean isMigrated() {
        return this.isMigrated;
    }

    public synchronized void buildVirtualPath(SwitchType type, OFFlowMod oriFlowMod, OVXSwitch ovxSwitch) {
        short outport = -1;
        short inport = -1;

        if(this.isBuild() == true) {
            //this.log.info("FlowID [{}] is already built", this.flowID);
            return;
        }

        Node node = new Node(ovxSwitch, oriFlowMod, type);
        inport = oriFlowMod.getMatch().get(MatchField.IN_PORT).getShortPortNumber();

        for(OFAction action : oriFlowMod.getActions()) {
            if(action.getType() == OFActionType.OUTPUT) {
                outport = ((OFActionOutput)action).getPort().getShortPortNumber();
                break;
            }
        }

        if(inport != -1 && outport != -1) {
            node.setInPort(ovxSwitch.getPort(inport));
            node.setOutPort(ovxSwitch.getPort(outport));
        }

        setHosts(type, oriFlowMod, ovxSwitch);
        switch(type) {
            case INGRESS:
                if(this.getSrcSwitch() == null) {
                    this.setSrcSwitch(node);
                    this.flowModOVXSwitchMap.put(oriFlowMod, ovxSwitch);
                    this.ovxSwitchFlowModMap.put(ovxSwitch, oriFlowMod);

                    this.log.info("INGRESS is set for FlowID {}", this.getFlowID());
                }
                break;
            case INTERMEDIATE:
            	int nodeHash = (int) (this.getFlowID() + node.getSwitch().getSwitchId().intValue());
                if(!(this.getIntermediateHash().contains(nodeHash))) {
                    this.getIntermediate().add(node);
                    this.getIntermediateHash().add(nodeHash);
                    this.flowModOVXSwitchMap.put(oriFlowMod, ovxSwitch);
                    this.ovxSwitchFlowModMap.put(ovxSwitch, oriFlowMod);

                    this.log.info("INTERMEDIATE is set for FlowID {}, N: {}", this.getFlowID(), this.getIntermediate().size());
                }
                break;
            case EGRESS:
                if(this.getDstSwitch() == null) {
                    this.setDstSwitch(node);
                    this.flowModOVXSwitchMap.put(oriFlowMod, ovxSwitch);
                    this.ovxSwitchFlowModMap.put(ovxSwitch, oriFlowMod);

                    this.log.info("EGRESS is set for FlowID {}", this.getFlowID());
                }
                break;
            case SAME:
                if(this.getSame() == null) {
                    this.setSame(node);
                    this.flowModOVXSwitchMap.put(oriFlowMod, ovxSwitch);
                    this.ovxSwitchFlowModMap.put(ovxSwitch, oriFlowMod);
                }
                break;
        }

        if(!this.isBuild())
            this.setBuild(isBuildVirtualPath());
    }

    public void setHosts(SwitchType type, OFFlowMod oriFlowMod, OVXSwitch ovxSwitch) {
        if(this.getSrcHost() != null && this.getDstHost() != null )
            return;

        OVXNetwork vnet = null;
        Collection<Host> hosts = null;
        try {
            vnet = ovxSwitch.getMap().getVirtualNetwork(this.getTenantID());
            hosts = vnet.getHosts();
        } catch (NetworkMappingException e) {
            e.printStackTrace();
        }

        if(this.getSrcHost() == null) {
            MacAddress srcMacAddress = oriFlowMod.getMatch().get(MatchField.ETH_SRC);
            for(Host host : hosts) {
                if (host.getMac().equals(srcMacAddress)) {
                    if(type == SwitchType.INGRESS) {
                        this.setSrcHost(host);
                        log.info("Ingress Host is set {} {}", host.getMac(), IPv4Address.of(host.getIp().getIp()));
                    }
                    break;
                }
            }
        }

        if(this.getDstHost() == null) {
            MacAddress dstMacAddress = oriFlowMod.getMatch().get(MatchField.ETH_DST);
            for(Host host : hosts) {
                if (host.getMac().equals(dstMacAddress)) {
                    if(type == SwitchType.EGRESS) {
                        this.setDstHost(host);
                        log.info("Egress Host is set {} {}", host.getMac(), IPv4Address.of(host.getIp().getIp()));
                    }
                    break;
                }
            }
        }
    }



    public synchronized boolean isBuildVirtualPath() {
        if (this.getSame() == null) {
            if (this.getSrcHost() == null || this.getDstHost() == null ||
                    this.getSrcSwitch() == null || this.getDstSwitch() == null) {
                return false;
            }
        } else {
            if (this.getSrcHost() == null || this.getDstHost() == null) {
                return false;
            } else {
                return true;
            }
        }

        if(this.getSrcHost().getPort().equals(this.getSrcSwitch().getInPort())) {
            log.debug("srcHost is connected to srcSwitch");
        }else {
            log.info("srcHost is not connected to srcSwitch");
            return false;
        }

        if(this.getDstHost().getPort().equals(this.getDstSwitch().getOutPort())) {
            log.debug("dstHost is connected to dstSwitch");
        }else {
            log.info("dstHost is not connected to dstSwitch");
            return false;
        }

        OVXLink outLink = (OVXLink)this.getSrcSwitch().getOutPort().getLink().getOutLink();
        if(outLink.getDstPort().equals(getDstSwitch().getInPort())) {
            log.debug("srcSwitch is connected to dstSwitch");

            return true;
        }else{
            log.info("srcSwitch is not connected to dstSwitch");

            List<Node> sortedNodes = new LinkedList<>();
            List<Node> clonedNodes = new LinkedList<>();
            clonedNodes.addAll(this.getIntermediate());

            if(this.getIntermediate().size() == 0)
                return false;

            Node curNode = this.getSrcSwitch();
            log.info("sortedNodes.size(): {}", sortedNodes.size());
            while(sortedNodes.size() != this.getIntermediate().size()) {
            	
                Node node = checkNextSwitch(curNode, clonedNodes);

                if (node != null) {
                    sortedNodes.add(node);
                    clonedNodes.remove(node);
                    curNode = node;
                    log.info("Find intermediate switch");
                }else{
                    log.info("Next switch does not exist");
                    return false;
                }
            }


            outLink = (OVXLink)curNode.getOutPort().getLink().getOutLink();
            if(outLink.getDstPort().equals(getDstSwitch().getInPort())) {
                log.info("VirtualPath ID [{}] is built", this.getPathID());
                return true;
            }else{
                return false;
            }
        }
    }
    

    public Node checkNextSwitch(Node cur, List<Node> nodes) {
        for(Node node : nodes) {
            OVXLink outLink = (OVXLink)cur.getOutPort().getLink().getOutLink();
            if(outLink.getDstPort().equals(node.getInPort())) {
                log.debug("Find next node");
                return node;
            }
        }
        return null;
    }

    public OFFlowMod removeVirtualPath(OFFlowMod delFlowMod, OVXMatch ovxMatch) {
        OVXSwitch sw = ovxMatch.getOVXSwitch();
        OFFlowMod ofFlowMod = this.ovxSwitchFlowModMap.get(sw);
        Node node = null;
        // ksyang
//        if(ofFlowMod == null){
//        	return null;
//        }
        
        if(ofFlowMod.getMatch().equals(delFlowMod.getMatch()) && ofFlowMod.getCookie().equals(delFlowMod.getCookie())) {
            this.ovxSwitchFlowModMap.remove(sw);
            this.flowModOVXSwitchMap.remove(ofFlowMod);

            if(this.getSrcSwitch() != null && this.getSrcSwitch().getOriFlowMod().equals(ofFlowMod)) {
                log.info("Removed SwitchType.INGRESS {}", delFlowMod.toString());
                this.setSrcSwitch(null);
                ovxMatch.setSwitchType(SwitchType.INGRESS);
                return ofFlowMod;
            }

            node = isContained(this.getIntermediate(), ofFlowMod);
            if(node != null) {
                log.info("Removed SwitchType.INTERMEDIATE {}", delFlowMod.toString());
                this.getIntermediate().remove(node);
                ovxMatch.setSwitchType(SwitchType.INTERMEDIATE);
                return ofFlowMod;
            }

            if(this.getDstSwitch() != null && this.getDstSwitch().getOriFlowMod().equals(ofFlowMod)) {
                log.info("Removed SwitchType.EGRESS {}", delFlowMod.toString());
                this.setDstSwitch(null);
                ovxMatch.setSwitchType(SwitchType.EGRESS);
                return ofFlowMod;
            }

            if(this.getSame() != null && this.getSame().getOriFlowMod().equals(ofFlowMod)) {
                log.info("Removed SwitchType.SAME {}", delFlowMod.toString());
                this.setSame(null);
                ovxMatch.setSwitchType(SwitchType.SAME);
                return ofFlowMod;
            }

            return null;
        }else{
            log.debug("Match is not matching {}/{}", delFlowMod.getMatch().toString(),
                    ofFlowMod.getMatch().toString());
            return null;
        }
    }

    public Node isContained(Queue<Node> nodes, OFFlowMod ofFlowMod) {
    	for (Node node: nodes){
    		if(node.getOriFlowMod().equals(ofFlowMod)){
                return node;
            }
    	}
    	
    	return null;
    }

    public synchronized boolean isRemoveVirtualPath() {
        if(this.getDstSwitch() != null) {
            log.debug("this.getDstSwitch() != null");
            this.isRemoved = false;
            return this.isRemoved;
        }

        if(this.getSrcSwitch() != null) {
            log.debug("this.getSrcSwitch() != null");
            this.isRemoved = false;
            return this.isRemoved;
        }

        if(this.getIntermediate().size() != 0) {
            log.debug("this.getIntermediate().size() != 0");
            this.isRemoved = false;
            return this.isRemoved;
        }

        if(this.getSame() != null) {
            log.debug("this.getSame() != null");
            this.isRemoved = false;
            return this.isRemoved;
        }

        this.isRemoved = true;
        return this.isRemoved;
    }

//    public String printVirtualPathInfo() {
//        String str = "\nFlowID = " + this.getFlowID() + "\n";
//        str = str + "TenantID = " + this.getTenantID() + "\n";
//        if(this.getSrcSwitch() != null) {
//            str = str + "SrcSwitch = " + this.getSrcSwitch().toString() + "\n";
//        }
//
//        if(this.getIntermediate().size() != 0) {
//            for(Node node : this.getIntermediate()) {
//                //log.info("Intermediate = " + node.toString());
//                str = str + "Intermediate = " + node.toString() + "\n";
//            }
//        }
//
//        if(this.getDstSwitch() != null) {
//            str = str + "DstSwitch = " + this.getDstSwitch().toString() + "\n";
//        }
//
//        if(this.pPath != null)
//            str = str + pPath.printPhysicalPathInfo();
//
//        if(this.mPath != null)
//            str = str + mPath.printPhysicalPathInfo();
//
//
//
//        return str;
//    }
}
