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

import net.onrc.openvirtex.elements.OVXMap;
import net.onrc.openvirtex.elements.datapath.PhysicalSwitch;
import net.onrc.openvirtex.elements.datapath.Switch;
import net.onrc.openvirtex.elements.host.Host;
import net.onrc.openvirtex.elements.link.Link;
import net.onrc.openvirtex.elements.link.OVXLink;
import net.onrc.openvirtex.elements.link.PhysicalLink;
import net.onrc.openvirtex.elements.port.PhysicalPort;
import net.onrc.openvirtex.elements.port.Port;
import net.onrc.openvirtex.exceptions.IndexOutOfBoundException;
import net.onrc.openvirtex.exceptions.LinkMappingException;
import net.onrc.openvirtex.messages.OVXMessage;
import net.onrc.openvirtex.routing.ShortestPath;
import net.onrc.openvirtex.services.forwarding.mpls.MplsLabel;
import net.onrc.openvirtex.services.path.Node;
import net.onrc.openvirtex.services.path.Path;
import net.onrc.openvirtex.services.path.PathUtil;
import net.onrc.openvirtex.services.path.SwitchType;
import net.onrc.openvirtex.services.path.physicalflow.PhysicalFlow;
import net.onrc.openvirtex.services.path.physicalpath.PhysicalPath.PathType;
import net.onrc.openvirtex.services.forwarding.mpls.MplsForwarding;
import net.onrc.openvirtex.services.path.virtualpath.VirtualPath;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.projectfloodlight.openflow.protocol.OFActionType;
import org.projectfloodlight.openflow.protocol.OFBucket;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowDelete;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.OFGroupDelete;
import org.projectfloodlight.openflow.protocol.OFGroupMod;
import org.projectfloodlight.openflow.protocol.OFGroupType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionGroup;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionSetField;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;

import java.math.BigInteger;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public class PhysicalPath extends Path {
    private static Logger log = LogManager.getLogger(PhysicalPath.class.getName());
    private static OFFactory factory;

    private Map<OFFlowMod, Node> oFlowModmFlowModMap;


	public enum PathType {
		MAIN, SUB, RECOVERY
	};
    
    private VirtualPath vPath;

    private boolean isMigrated;
    private boolean isOriginalPath;

    private Integer originalPathID;

    private MplsLabel label = null;
    
    private PhysicalFlow physicalFlow = null;
    
    private PathType pathType = PathType.MAIN;
    
    private boolean isValid = true;
    private boolean isAssigned = false;
    
    private PhysicalPath primaryPath = null;
    private int assignedThroughput = 0;
    
    private List<PhysicalLink> outlinks = Collections.synchronizedList(new LinkedList<PhysicalLink>());
    private List<PhysicalLink> inlinks = Collections.synchronizedList(new LinkedList<PhysicalLink>());
    
    public PhysicalPath(int flowID, int tenantID, int pathID, VirtualPath vPath, PathType pathType) {
        super(flowID, tenantID, pathID);
        this.oFlowModmFlowModMap = new HashMap<>();
        this.vPath = vPath;
        if(pathType != null){
        	this.pathType = pathType;
        }
        
        isMigrated = false;
        isOriginalPath = false;
        originalPathID = null;
        
        factory = OFFactories.getFactory(OFVersion.OF_13);
    }
    
    public boolean isValid(){
    	return isValid;
    }
    
    // Physical flow 
    public void setPhysicalFlow(PhysicalFlow pFlow){
    	this.physicalFlow = pFlow;
    }
    public PhysicalFlow getPhysicalFlow(){
    	return this.physicalFlow;
    }
    
    // Path type
    public void setPathType(PathType pathType){
    	this.pathType = pathType;
    }
    public PathType getPathType(){
    	return this.pathType;
    }
    public void setPrimaryPath(PhysicalPath primaryPath){
    	this.primaryPath = primaryPath;
    }
    public PhysicalPath getPrimaryPath(){
    	return this.primaryPath;
    }
    public boolean isSubPath(){
    	if(this.pathType == PathType.SUB){
    		return true;
    	}
    	return false;
    }
    public boolean isPrimaryPath(){
    	if(this.pathType == PathType.MAIN){
    		return true;
    	}
    	return false;
    }
    
    // Throughput
    public void setBandwidth(int assignedThroughput){
    	this.assignedThroughput = assignedThroughput;
    }
    
    public int getAssignedThroughput(){
    	return this.assignedThroughput;
    }
    
    // MPLS label
    public MplsLabel getMplsLabel() {
        return this.label;
    }
    
    // Sub and RECOVERY paths don`t have MplsLabel, just can get a label value;
    public int getLabelValue(){
    	int mplsValue = 0;
    	
    	if(this.pathType == PathType.MAIN && this.getMplsLabel() != null){
    		mplsValue = this.getMplsLabel().getLabelValue(); // 00
    	}
    	else if(this.pathType == PathType.RECOVERY){
    		mplsValue = this.getPrimaryPath().getLabelValue() + 1; // 01
    	}
    	else if(this.pathType == PathType.SUB){
    		mplsValue = this.getPrimaryPath().getLabelValue() + 2; // 10
    	}
    	
    	return mplsValue;
    }

    // Path info for debugging
    public String getPathInfo() { 
    	String pathInfo = new String();
    	pathInfo += "{";
    	pathInfo += "Tid: " + this.getTenantID();
    	if(this.getSrcHost() != null && this.getDstHost() != null){
    		pathInfo += ", srcHost: " + this.getSrcHost().getIp().toSimpleString() + ", dstHost: " + this.getDstHost().getIp().toSimpleString();
    	}
    	pathInfo += ", FlowID: " + this.getPhysicalFlow().getFlowId();
		pathInfo += ", PathID: [" + this.getPathType()+ "]" + this.getPathID();
		pathInfo += ", Alloc: " + this.getAssignedThroughput();
		int mplsValue = this.getLabelValue();
		int mask = 0xff;
    	String ipStr = 
    	  String.format("%d.%d.%d.%d",
    	         (mplsValue >> 24 & mask),   
    	         (mplsValue >> 16 & mask),             
    	         (mplsValue >> 8 & mask),    
    	         (mplsValue >> 0 & mask));
    	
    	pathInfo += ", MPLS: "+ mplsValue + "(" + ipStr + ")";
    	pathInfo += "}";
    	
    	return pathInfo;
    }
    
    // Assigned
    public boolean isAssigned(){
    	return this.isAssigned;
    }
    
    public void setItselfOriginalPathID(MplsLabel label) {
        this.isOriginalPath = true;
        this.originalPathID = this.getPathID();
        this.label = label;
    }

    public void setNotOriginalPathID(MplsLabel label) {
        this.isOriginalPath = false;
        this.originalPathID = label.getOriginalPathID();
        this.label = label;
    }

    public int getOriginalPathID() {
        return this.originalPathID;
    }

    public boolean isOriginalPath() {
        return this.isOriginalPath;
    }

    public VirtualPath getvPath() {
        return this.vPath;
    }

    public OFFlowMod getModifiedFlowMod(OFFlowMod ofFlowMod) {
        return this.oFlowModmFlowModMap.get(ofFlowMod).getOriFlowMod();
    }

    // Functions
    public List<PhysicalSwitch> getSwitches() {
        LinkedList<PhysicalSwitch> switches = new LinkedList<>();

        for(Node node : this.oFlowModmFlowModMap.values()) {
            switches.add((PhysicalSwitch)node.getSwitch());
        }

        return switches;
    }
    
    public List<Node> getNodeList(){
    	List<Node> nodes = new LinkedList<Node>();
    	nodes.add(this.getSrcSwitch());
		for(Node sw: this.getIntermediate()){
			nodes.add(sw);
		}
		nodes.add(this.getDstSwitch());
		return nodes;
    }

    public Collection<Node> getNodes() {
        return this.oFlowModmFlowModMap.values();
    }

//    public String printPhysicalPathInfo() {
//        String str = "PathID = " + this.getPathID() + "\n";
//        str = str + "FlowID = " + this.getFlowID() + "\n";
//        str = str + "TenantID = " + this.getTenantID() + "\n";
//        str = str + "MPLS = " + this.getPathInfo() + "\n";
//
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
//        if(this.originalPathID != null) {
//            str = str + "OriginalPathFlowID = " + this.originalPathID + " [" + this.isOriginalPath + "]\n";
//        }
//
//        return str;
//    }
    
    public PhysicalPath buildPhysicalPath(VirtualPath vPath, OFFlowMod oriFlowMod, OFFlowMod mFlowMod, SwitchType type,
                                       PhysicalSwitch psw) throws IndexOutOfBoundException, LinkMappingException{
    	log.info("buildPhysicalPath(): {}", this.getPathInfo());
        // log.info("FlowID " + this.getFlowID() + " Physical Path is building");
        OFFlowMod mplsFlowMod = null;
        Node node = null;

        //System.out.printf("isBuildPhysicalPath [%s]\n", ofMessage.toString());
        if(vPath.getSrcHost() != null && this.getSrcHost() == null) {
            this.setSrcHost(vPath.getSrcHost());
        }

        if(vPath.getDstHost() != null && this.getDstHost() == null) {
            this.setDstHost(vPath.getDstHost());
        }
        
        switch(type) {
            case INGRESS:
                if(this.getSrcSwitch() == null) {
                    log.info("FlowID [" + this.getFlowID() + "] SrcSwitch is set");
                	node = new Node();
                    mplsFlowMod = MplsForwarding.getInstance().addMplsActions(this, vPath, mFlowMod, type, node);
                    node.initiate(psw, mplsFlowMod, mFlowMod, oriFlowMod, type);
                    
                    OVXLink vlink = (OVXLink)this.getvPath().getSrcSwitch().getOutPort().getLink().getOutLink();
            		PhysicalPort physicalOutPort = OVXMap.getInstance().getPhysicalLinks(vlink).get(0).getSrcPort();
            		node.setOutPort(physicalOutPort);
            		
                    this.setSrcSwitch(node);
                    this.oFlowModmFlowModMap.put(oriFlowMod, node);
                }else{
                    mplsFlowMod = this.getSrcSwitch().getMplsFlowMod();
                }
                break;
            case INTERMEDIATE:
                if(this.oFlowModmFlowModMap.get(oriFlowMod) == null) {
                	log.info("{} is in INTERMEDIATE()", this.getPathInfo());
                    //log.info("FlowID [" + this.getFlowID() + "] INTERMEDIATE is set");
                    //mplsFlowMod = MplsForwarding.getInstance().addMplsActions(this, vPath, mFlowMod, type);
                	node = new Node();
                	mplsFlowMod = MplsForwarding.getInstance().addMplsActions(this, vPath, mFlowMod, type, node);
                	node.initiate(psw, mplsFlowMod, mFlowMod, oriFlowMod, type);
                    this.oFlowModmFlowModMap.put(oriFlowMod, node);

                    this.getIntermediate().clear();

                    for (Node n : vPath.getIntermediate()) {
                        Node targetNode = this.oFlowModmFlowModMap.get(n.getOriFlowMod());
                        
                        if(targetNode != null) {
                            //log.info("FlowID [" + this.getFlowID() + "] INTERMEDIATE is set {}", targetNode.toString());
                        	
                        	OVXLink vlink = (OVXLink)n.getInPort().getLink().getInLink();
                    		PhysicalPort physicalInPort = OVXMap.getInstance().getPhysicalLinks(vlink).get(0).getDstPort();
                    		targetNode.setInPort(physicalInPort);
                        	
                        	vlink = (OVXLink)n.getOutPort().getLink().getOutLink();
                    		PhysicalPort physicalOutPort = OVXMap.getInstance().getPhysicalLinks(vlink).get(0).getSrcPort();
                    		targetNode.setOutPort(physicalOutPort);
                        	
                            this.getIntermediate().add(targetNode);
                        }
                    }
                    if(!this.isOriginalPath()){
                    	this.getIntermediate().add(node);
                    }
                    
                }else{
                    mplsFlowMod = this.oFlowModmFlowModMap.get(oriFlowMod).getMplsFlowMod();
                }
                break;
            case EGRESS:
                if(this.getDstSwitch() == null) {
                    log.info("FlowID [" + this.getFlowID() + "] DstSwitch is set");
                	node = new Node();
                    mplsFlowMod = MplsForwarding.getInstance().addMplsActions(this, vPath, mFlowMod, type, node);
                    node.initiate(psw, mplsFlowMod, mFlowMod, oriFlowMod, type);

                    OVXLink vlink = (OVXLink)this.getvPath().getDstSwitch().getInPort().getLink().getInLink();
            		PhysicalPort physicalInPort = OVXMap.getInstance().getPhysicalLinks(vlink).get(0).getDstPort();
            		node.setInPort(physicalInPort);
                    
                    this.setDstSwitch(node);
                    this.oFlowModmFlowModMap.put(oriFlowMod, node);
                }else{
                    mplsFlowMod = this.getDstSwitch().getMplsFlowMod();
                }
                break;
            case SAME:
                if(this.getSame() == null) {
                    log.info("this.same == null");
                    this.setSame(new Node(psw, mFlowMod, mFlowMod, oriFlowMod, type));
                }else{
                    log.info("this.same != null");
                }
                break;
        }
        
        if(!this.isBuild())
            this.setBuild(isBuildPhysicalPath());
        
        return this;
    }
    
    public synchronized LinkedList<PhysicalLink> calculateShortestPath(PhysicalSwitch srcSwitch, PhysicalSwitch dstSwitch){
    	
    	ShortestPath stp = new ShortestPath();
    	stp.setPhysicalFlow(this.getPhysicalFlow());
    	
    	LinkedList<PhysicalLink> stpLinks;
        stpLinks = stp.computePath(srcSwitch, dstSwitch);
        
        String subpath = new String();
        int linkCost = 0;
        for(PhysicalLink link: stpLinks){
        	subpath += "["+ link.toString() + "]";
        	linkCost += link.getAssignedBandwidth() + 1;
        }
        log.info("{} newPath: {}, LinkCost: {}",this.getPathInfo(), subpath, linkCost);
        
    	return stpLinks;
    }
    
    public synchronized boolean buildSubPhysicalPath(){
    	PhysicalPath oripPath = this.primaryPath;
        if(this.isBuild())
        	return true;
        
        // Set Host 
        this.setSrcHost(vPath.getSrcHost());
        this.setDstHost(vPath.getDstHost());
        
        // Invalidate main path
        Node oriDstNode = oripPath.getDstSwitch();
        Node oriSrcNode = oripPath.getSrcSwitch();
        if(oriDstNode == null || oriSrcNode == null || oripPath.getIntermediate().isEmpty())
        	return true;
        
        // Calculate shortest path from srcSwitch to dstSwitch
        PhysicalSwitch srcSwitch = (PhysicalSwitch) oriSrcNode.getSwitch();
        PhysicalSwitch dstSwitch = (PhysicalSwitch) oriDstNode.getSwitch();
        LinkedList<PhysicalLink> stpLinks = calculateShortestPath(srcSwitch, dstSwitch);
        
        // <Source switch>
        buildSrcSwitch(stpLinks);

    	// <Intermediate switches>
        buildSubIntermediate(stpLinks);
    	
        // <Destination switch>
        buildDstSwitch(stpLinks);
        
        // Build complete
        if(!this.isBuild())
        	this.setBuild(this.isBuildPhysicalPath());
        this.isValid = true;
        
        log.info("buildSubPhysicalPath() is done: {}", getPathInfo());
    	return true;
    }
    
    private boolean buildSrcSwitch(LinkedList<PhysicalLink> newPathLinks){
    	Node oriSrcNode = this.primaryPath.getSrcSwitch();
        PhysicalPort outPort = newPathLinks.getFirst().getSrcPort();
    	
        // Match
        Match match = oriSrcNode.getMplsFlowMod().getMatch().createBuilder().build();
        
        // Actions
        List<OFAction> actions = new LinkedList<OFAction>();
        // 1. Attach Label
        OFActionSetField actionSetIPv4src = factory.actions().buildSetField()
                .setField(factory.oxms().ipv4Src(IPv4Address.of(this.getLabelValue())))
                .build();
        actions.add(actionSetIPv4src);
        // 2. Output
        OFActionOutput output = factory.actions().buildOutput()
                .setPort(OFPort.of(outPort.getPortNumber())).build();
        actions.add(output);
        
        // Build FlowMod
        OFFlowMod mplsFlowMod = this.getPrimaryPath().getSrcSwitch().getMplsFlowMod().createBuilder()
        		.setMatch(match).setActions(actions).build();
        
        // Create Node
        Node srcNode = new Node();
        srcNode.setOutPort(outPort);
        srcNode.initiate(oriSrcNode.getSwitch(), mplsFlowMod, mplsFlowMod, mplsFlowMod, SwitchType.INGRESS);
        
        // Set node
        this.setSrcSwitch(srcNode);
        
    	return true;
    }
    
    private boolean buildSubIntermediate(LinkedList<PhysicalLink> newPathLinks){
        // For link each subPath
        PhysicalLink prevLink = newPathLinks.getFirst();
        for(PhysicalLink link: newPathLinks){
        	if(link.equals(newPathLinks.getFirst())){ // srcNode
                continue;
        	}
            PhysicalPort inPort = prevLink.getDstPort();
            PhysicalPort outPort = link.getSrcPort();
        	
        	// Match
            Node oriDstNode = this.getPrimaryPath().getDstSwitch();
            Match match = oriDstNode.getMplsFlowMod().getMatch().createBuilder()
                    .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                    .setExact(MatchField.IPV4_SRC, IPv4Address.of(this.getLabelValue()))
                    .build();
            
            // Actions
            List<OFAction> actions = new LinkedList<OFAction>();
            // Output
            OFActionOutput output = factory.actions().buildOutput()
                    .setPort(OFPort.of(outPort.getPortNumber())).build();
            actions.add(output);

            OFFlowMod PriDstFlowMod = this.getPrimaryPath().getDstSwitch().getMplsFlowMod();
            // Build FlowMod
            OFFlowMod mplsFlowMod = PriDstFlowMod.createBuilder()
            		.setMatch(match).setActions(actions).build();
            
            // Build FlowAdd
            U64 cookie = PriDstFlowMod.getCookie();
            long xid = PriDstFlowMod.getXid();
            Set<OFFlowModFlags> flags = PriDstFlowMod.getFlags();
            int priority = PriDstFlowMod.getPriority();
            OFBufferId bufferId = PriDstFlowMod.getBufferId();
            OFFlowMod mplsFlowAdd = factory.buildFlowAdd().setPriority(priority).setCookie(cookie)
            		.setXid(xid).setFlags(flags).setBufferId(bufferId)
            		.setMatch(match).setActions(actions).build();
            
            // Create Node
            Node node = new Node();
            node.setInPort(inPort);
            node.setOutPort(outPort);
            node.initiate(link.getSrcSwitch(), mplsFlowMod, mplsFlowMod, mplsFlowMod, SwitchType.INTERMEDIATE);
            node.setMplsFlowAdd(mplsFlowAdd);
            
            // Add node
            this.getIntermediate().add(node);
            
            prevLink = link;
        }
    	return true;
    }
    
    private boolean buildDstSwitch(LinkedList<PhysicalLink> newPathLinks){
    	Node oriDstNode = this.primaryPath.getDstSwitch();
        PhysicalPort inPort = newPathLinks.getLast().getDstPort();
    	
        // Match
        Match match = oriDstNode.getMplsFlowMod().getMatch().createBuilder()
                .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                .setExact(MatchField.IPV4_SRC, IPv4Address.of(this.getLabelValue()))
                .build();
        
        // Actions
        List<OFAction> actions = oriDstNode.getMplsFlowMod().getActions();

        OFFlowMod PriDstFlowMod = this.getPrimaryPath().getDstSwitch().getMplsFlowMod();
        // Build FlowMod
        OFFlowMod mplsFlowMod = PriDstFlowMod.createBuilder()
        		.setMatch(match).setActions(actions).build();
        
        // Build FlowAdd
        U64 cookie = PriDstFlowMod.getCookie();
        long xid = PriDstFlowMod.getXid();
        Set<OFFlowModFlags> flags = PriDstFlowMod.getFlags();
        int priority = PriDstFlowMod.getPriority();
        OFBufferId bufferId = PriDstFlowMod.getBufferId();
        OFFlowMod mplsFlowAdd = factory.buildFlowAdd().setPriority(priority).setCookie(cookie)
        		.setXid(xid).setFlags(flags).setBufferId(bufferId)
        		.setMatch(match).setActions(actions).build();
        
        // Create Node
        Node dstNode = new Node();
        dstNode.setInPort(inPort);
        dstNode.initiate(oriDstNode.getSwitch(), mplsFlowMod, mplsFlowMod, mplsFlowMod, SwitchType.EGRESS);
        dstNode.setMplsFlowAdd(mplsFlowAdd);
        
        // Set node
        this.setDstSwitch(dstNode);
        
    	return true;
    }
    
    /* Delete this path in the physical network(Remove the flow rules)*/
    
    public synchronized boolean deleteSecondaryPath() throws LinkMappingException{
    	if(!isAssigned)
    		return true;
    	
    	// Clear Source
    	this.getSrcSwitch().setMplsFlowAdd(null);
    	this.getSrcSwitch().setMplsFlowMod(null);
    	this.getSrcSwitch().setMplsGroupAdd(null);
    	this.getSrcSwitch().setMplsGroupMod(null);
    	
    	// Clear Intermediates
    	this.getIntermediate().clear();
    	
    	// Clear Destination
    	this.getDstSwitch().setMplsFlowAdd(null);
    	this.getDstSwitch().setMplsFlowMod(null);
    	
    	// Unassign Bandwidth
    	this.setBandwidth(0);
    	this.unAssignFromLinks();
    	this.showBandwidthInfo();
		this.setBuild(false);
		
		this.isValid = false;
    	return true;
    }
    
    public synchronized boolean isBuildPhysicalPath() {
    	if(this.isBuild())
    		return true;
    	
        if (this.getSame() == null) {
            if (this.getSrcHost() == null || this.getDstHost() == null ||
                    this.getSrcSwitch() == null || this.getDstSwitch() == null) {
            	//log.info("PathID[{}]: isbuild Fail 1", this.getPathID());
                return false;
            }
        } else {
            if (this.getSrcHost() == null || this.getDstHost() == null) {
            	//log.info("PathID[{}]: isbuild Fail 2", this.getPathID());
                return false;
            } else {
                return true;
            }
        }

        PhysicalLink outLink = (PhysicalLink)this.getSrcSwitch().getOutPort().getLink().getOutLink();
        if(outLink.getDstPort().equals(getDstSwitch().getInPort())) {
            log.debug("srcSwitch is connected to dstSwitch");

            return true;
        }else{
            log.debug("srcSwitch is not connected to dstSwitch");

            List<Node> sortedNodes = new LinkedList<>();
            List<Node> clonedNodes = new LinkedList<>();
            clonedNodes.addAll(this.getIntermediate());

            if(this.getIntermediate().size() == 0){
            	//log.info("PathID[{}]: isbuild Fail 3", this.getPathID());
                return false;
            }

            Node curNode = this.getSrcSwitch();
            //log.info("PathID[{}]: isbuild Fail a", this.getPathID());
            while(sortedNodes.size() != this.getIntermediate().size()) {

                Node node = checkNextSwitch(curNode, clonedNodes);

                if (node != null) {
                    sortedNodes.add(node);
                    clonedNodes.remove(node);
                    curNode = node;
                    log.info("Find intermediate switch");
                }else{
                    log.info("Next switch does not exist");
                    //log.info("PathID[{}]: isbuild Fail 3", this.getPathID());
                    return false;
                }
                //log.info("PathID[{}]: isbuild Fail b", this.getPathID());
            }


            outLink = (PhysicalLink)curNode.getOutPort().getLink().getOutLink();
            if(outLink.getDstPort().equals(getDstSwitch().getInPort())) {
                log.info("PhysicalPath ID [{}] is built", this.getPathID());
                return true;
            }else{
            	log.info("PathID[{}]: isbuild Fail 4", this.getPathID());
                return false;
            }
        }
    }
    
    public Node checkNextSwitch(Node cur, List<Node> nodes) {
        for(Node node : nodes) {
        	PhysicalLink outLink = (PhysicalLink)cur.getOutPort().getLink().getOutLink();
            if(outLink.getDstPort().equals(node.getInPort())) {
                log.debug("Find next node");
                return node;
            }
        }
        return null;
    }
    
    public List<PhysicalLink> calculateCorrespondLinks(){
    	List<PhysicalLink> links = new LinkedList<PhysicalLink>();
    	PhysicalLink plink = (PhysicalLink)this.getSrcSwitch().getOutPort().getLink().getOutLink();
    	links.add(plink);
    	for(Node node: this.getIntermediate()){
    		plink = (PhysicalLink)node.getOutPort().getLink().getOutLink();
    		links.add(plink);
    	}
    	
    	return links;
    }
    
    public List<Entry<PhysicalPath, Integer>> findOverlappedMainPaths(){
    	List<Entry<PhysicalPath, Integer>> victims = new LinkedList<Entry<PhysicalPath, Integer>>();
    	ConcurrentHashMap<PhysicalPath, Integer> candidates = new ConcurrentHashMap<PhysicalPath, Integer>();
    	List<PhysicalLink> links = calculateCorrespondLinks();
    	
    	this.log.info("{}: NOT possible to assign", this.getPathInfo());
    	
    	//only check outlink because outlink.avail is equal to inlink.avail
    	for(PhysicalLink link: links){
    		for(PhysicalPath path: link.getMappedPath()){
    			if(path.isPrimaryPath()){ 
    				if(path.getPhysicalFlow().getRequiredThroughput() == 0){
    					continue; // It`s a ack flow (ignore)
    				}
    				
    				if(this.getSrcHost() == path.getSrcHost() && this.getDstHost() == path.getDstHost()){
    					continue; // It`s me!
    				}
    				if(candidates.contains(path)){
    					//update candidate throughput
    					candidates.put(path, candidates.get(path) + path.getAssignedThroughput());
    				}
    				else{
    					//add candidate
    					candidates.put(path, path.getAssignedThroughput());
    				}
    			}
    		}
    	}
    	//sort candidates by desc
    	if(!candidates.isEmpty()){
    		victims = sortByComparator(candidates, false); // desc
    	}
    	return victims;
    }
    
    public List<Entry<PhysicalPath, Integer>> findOverlappedSubPaths(){
    	List<Entry<PhysicalPath, Integer>> victims = new LinkedList<Entry<PhysicalPath, Integer>>();
    	ConcurrentHashMap<PhysicalPath, Integer> candidates = new ConcurrentHashMap<PhysicalPath, Integer>();
    	List<PhysicalLink> links = calculateCorrespondLinks();
    	
    	this.log.info("{}: NOT possible to assign", this.getPathInfo());
    	
    	//only check outlink because outlink.avail is equal to inlink.avail
    	for(PhysicalLink link: links){
    		for(PhysicalPath path: link.getMappedPath()){
    			if(path.getPathType() != PathType.MAIN){ 
    				if(candidates.contains(path)){
    					candidates.put(path, candidates.get(path) + path.getAssignedThroughput());
    				}
    				else{
    					//add candidate
    				candidates.put(path, path.getAssignedThroughput());
    				}
    			}
    		}
    	}
    	//sort candidates by desc
    	if(!candidates.isEmpty()){
    		victims = sortByComparator(candidates, false); // desc
    	}
    	return victims;
    }
    
    private static List<Entry<PhysicalPath, Integer>> sortByComparator(Map<PhysicalPath, Integer> unsortMap, final boolean order)
    {
    	//public static boolean ASC = true;
        //public static boolean DESC = false;
        List<Entry<PhysicalPath, Integer>> list = new LinkedList<Entry<PhysicalPath, Integer>>(unsortMap.entrySet());

        // Sorting the list based on values
        Collections.sort(list, new Comparator<Entry<PhysicalPath, Integer>>()
        {
            public int compare(Entry<PhysicalPath, Integer> o1,
                    Entry<PhysicalPath, Integer> o2)
            {
                if (order)
                {
                    return o1.getValue().compareTo(o2.getValue());
                }
                else
                {
                    return o2.getValue().compareTo(o1.getValue());

                }
            }
        });
        return list;
    }
    
    
    // Map physical links (both outlinks and inlinks) to physical path
    private void assignToLink(PhysicalPort pport){
    	if(!this.outlinks.contains(pport.getLink().getOutLink())){
	    	this.outlinks.add(pport.getLink().getOutLink());
	    	this.inlinks.add(pport.getLink().getInLink());
    	}
    	
    	pport.getLink().getOutLink().assignToPath(this);
    	pport.getLink().getInLink().assignToPath(this);
    	
    	return;
    }
    
    // UnMap physical links (both outlinks and inlinks) to physical path
    private void unAssignToLink(PhysicalPort pport){
    	pport.getLink().getOutLink().unAssignToPath(this);
    	pport.getLink().getInLink().unAssignToPath(this);
    	
    	return;
    }
    
    // Assign a path to links
    synchronized public boolean assignToLinks() throws LinkMappingException{
    	Node srcNode = this.getSrcSwitch();
    	assignToLink((PhysicalPort)srcNode.getOutPort());// assume vlink:plink = 1:1
    	for(Node node: this.getIntermediate()){
    		assignToLink((PhysicalPort)node.getOutPort());
    	}
    	isAssigned = true;
    	log.info("{} is assigned", this.getPathInfo());
    	return true;
    }
    
 // UnAssign Bandwidth to Physical Path
    synchronized public boolean unAssignFromLinks() throws LinkMappingException{
//    	if(!isAssigned){
//    		log.info("{} is alreay unassigned", this.getPathInfo());
//    		return true;	
//    	}
    	Node srcNode = this.getSrcSwitch();
    	unAssignToLink((PhysicalPort)srcNode.getOutPort());// assume vlink:plink = 1:1
    	for(Node node: this.getIntermediate()){
    		unAssignToLink((PhysicalPort)node.getOutPort());
    	}
    	this.outlinks.clear();
    	this.inlinks.clear();
    	isAssigned = false;
    	return true;
    }
    
    synchronized public boolean showBandwidthInfo() throws LinkMappingException{
    	if(!isAssigned){
    		log.info("{} is no assigned", this.getPathInfo());
    		return false;
    	}
    	
    	List<PhysicalLink> links = calculateCorrespondLinks();
    	String sublink = new String();
    	for(PhysicalLink link: links){
    		sublink += "["+ link.toString() + "]";
    	}
		log.info("{} - {} : assigned throughput is [{}]", 
    			this.getPathInfo(),
    			sublink,
    			this.assignedThroughput);
		
    	return true;
    }
    
    private boolean isValidFlowMod(Node node){
    	if((node != null) && (node.getMplsFlowMod() != null)){
    		return true;
    	}
    	return false;
    }
    

    public void sendSouth() {
    	//log.info("Send South PathID: {}", this.getPathID());
        //log.info("Send to physical switches for PhysicalPath [" + this.getFlowID() + "]");
    	Node dstNode = this.getDstSwitch();
        if(isValidFlowMod(dstNode)) {
            log.info("SendSouth Dst switch for {} ", this.getPathInfo());
        	if(dstNode.getMplsFlowAdd() != null){
        		dstNode.getSwitch().sendMsg(new OVXMessage(dstNode.getMplsFlowAdd()), dstNode.getSwitch());
        		dstNode.setMplsFlowAdd(null);
            }
        	dstNode.getSwitch().sendMsg(new OVXMessage(dstNode.getMplsFlowMod()), dstNode.getSwitch());
        }

        //if(this.isOriginalPath) {
        if (this.getIntermediate().size() != 0) {
            for (Node node : this.getIntermediate()) {
                if(isValidFlowMod(node)) {
                    log.info("SendSouth Inter switches for {} ", this.getPathInfo());
                    if(node.getMplsFlowAdd() != null){
                    	node.getSwitch().sendMsg(new OVXMessage(node.getMplsFlowAdd()), node.getSwitch());
                    	node.setMplsFlowAdd(null);
                    }
                    node.getSwitch().sendMsg(new OVXMessage(node.getMplsFlowMod()), node.getSwitch());
                    
                }
            }
        }
        //}
        Node srcNode = this.getSrcSwitch();
        if(isValidFlowMod(srcNode)) {
        	log.info("SendSouth src switch for {} ", this.getPathInfo());
        	if(this.getPhysicalFlow().getSrcSwitch().getSwitch() != null && this.isPrimaryPath()){ 
        		// Physical Flow: main path
        		srcNode = this.getPhysicalFlow().getSrcSwitch();
        		log.info("SrcNode: physicalFlow");
        		sendSouthGroup(srcNode);
        		log.info("Meter: Start");
        		sendSouthMeter(srcNode);
        		log.info("Meter: End");
        		srcNode.getSwitch().sendMsg(new OVXMessage(srcNode.getMplsFlowMod()), srcNode.getSwitch());
        	}
        	else if(this.getPhysicalFlow().getSrcSwitch().getSwitch() != null){ 
        		// Physical Flow: secondary path
        		; // nothing
        	}
        	else if(srcNode.getMplsFlowAdd() != null){ 
        		// Original sequence
            	srcNode.getSwitch().sendMsg(new OVXMessage(srcNode.getMplsFlowAdd()), srcNode.getSwitch());
            	srcNode.setMplsFlowAdd(null);
            	srcNode.getSwitch().sendMsg(new OVXMessage(srcNode.getMplsFlowMod()), srcNode.getSwitch());
            }
        }
        
    }

    public Node getCorrespondingNode(PhysicalSwitch psw) {
        if(getSrcSwitch() != null && getSrcSwitch().getSwitch().equals(psw)) {
            return getSrcSwitch();
        }else if(getDstSwitch() != null && getDstSwitch().getSwitch().equals(psw)) {
            return getDstSwitch();
        }else if(getIntermediate().size() != 0) {
            for(Node node : getIntermediate()) {
                if(node.getSwitch().equals(psw)) {
                    return node;
                }
            }
        }

        return null;
    }
    
    private void sendSouthGroup(Node node){
    	//log.info("SendSouth Group for FlowID [{}], PathID [{}]", this.getFlowID(), this.getPathID());
    	if((!node.isSentMplsGroupAdd()) && (node.getMplsGroupAdd() != null)){
    		node.getSwitch().sendMsg(new OVXMessage(node.getMplsGroupAdd()), node.getSwitch());
			node.setSentGroupAdd(true);
			
			if(node.getMplsGroupMod() != null){
				node.getSwitch().sendMsg(new OVXMessage(node.getMplsGroupMod()), node.getSwitch());
	    	}
        }
    	else if(node.getMplsGroupMod() != null){
    		node.getSwitch().sendMsg(new OVXMessage(node.getMplsGroupMod()), node.getSwitch());
    	}
    }
    private void sendSouthMeter(Node node){
        log.info("Meter: SendSouth Meter for FlowID [{}], PathID [{}]", this.getFlowID(), this.getPathID());
        if((!node.isSentMplsMeterAdd()) && (node.getMplsMeterAdd() != null)){
            node.getSwitch().sendMsg(new OVXMessage(node.getMplsMeterAdd()), node.getSwitch());
            log.info("Meter: SendMsg1");
            node.setSentMeterAdd(true);
            
            if(node.getMplsMeterMod() != null){
                node.getSwitch().sendMsg(new OVXMessage(node.getMplsMeterMod()), node.getSwitch());
                log.info("Meter: SendMsg2");
            }
        }
        else if(node.getMplsMeterMod() != null){
            node.getSwitch().sendMsg(new OVXMessage(node.getMplsMeterMod()), node.getSwitch());
            log.info("Meter: SendMsg3");
        }
        else{
        	node.getSwitch().sendMsg(new OVXMessage(node.getMplsMeterMod()), node.getSwitch());
            log.info("Meter: SendMsg4");
        }
    }
}
