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
 * Physical Flow is a new layer for managing relations between virtual path and physical paths.
 * The virtual path is created by tenant's controller (e.g., ONOS, ODL) and recognized through Path Virtualization.
 * For one virtual path, multiple physical paths are created.
 * 
 * The types of physical path are Main path, Recovery path and Sub path.
 * 
 * --- Primary path
 * 1. Main path (TP: Tenant path)
 * Main path is same as the route of the virtual path. (Primary path)
 * 
 * --- Secondary path
 * 2. Recovery path (SP: Stand-by path)
 * Recovery path is for fail-over.
 * 
 * 3. Sub path (AP: Active path)
 * Sub path is for traffic load balancing.
 * 
 * I initially designed that the number of physical paths is 1, 1, N respectively for a paper.
 * However, I think it is okay that the number of sub path is also 1 in the implementation.
 ******************************************************************************/
package net.onrc.openvirtex.services.path.physicalflow;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.AbstractMap;

import com.google.gson.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.projectfloodlight.openflow.protocol.OFBucket;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFGroupMod;
import org.projectfloodlight.openflow.protocol.OFGroupType;
import org.projectfloodlight.openflow.protocol.OFMeterFlags;
import org.projectfloodlight.openflow.protocol.OFMeterMod;
import org.projectfloodlight.openflow.protocol.OFMeterModCommand;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.meterband.OFMeterBand;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionGroup;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionSetField;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.ver13.OFMeterModCommandSerializerVer13;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

import net.onrc.openvirtex.elements.host.Host;
import net.onrc.openvirtex.elements.link.PhysicalLink;
import net.onrc.openvirtex.exceptions.IndexOutOfBoundException;
import net.onrc.openvirtex.exceptions.LinkMappingException;
import net.onrc.openvirtex.services.path.Node;
import net.onrc.openvirtex.services.path.physicalpath.PhysicalPath;
import net.onrc.openvirtex.services.path.physicalpath.PhysicalPath.PathType;
import net.onrc.openvirtex.services.path.physicalpath.PhysicalPathBuilder;
import net.onrc.openvirtex.services.path.virtualpath.VirtualPath;

public class PhysicalFlow {
	private static Logger log = LogManager.getLogger(PhysicalFlow.class.getName());
	private static OFFactory factory;
	
	private PhysicalPath mainPath = null;
	private PhysicalPath recoveryPath = null;
	private List<PhysicalPath> subPathList = Collections.synchronizedList(new LinkedList<PhysicalPath>());
	
	private Node srcSwitch = null;
	
	private int flowId = 0;
	private Host srcHost = null;
    private Host dstHost = null;
    private int requiredThroughput = 0;
    //private int assignedThroughput = 0; to function
    //private int usedThroughput = 0;
    
	public PhysicalFlow(int flowId){
		factory = OFFactories.getFactory(OFVersion.OF_13);
		this.flowId = flowId;
		this.srcSwitch = new Node();
	}
	
	public int getFlowId(){
		return flowId;
	}
	public String getPhysicalFlowInfo(){
		String flowInfo = new String();
		flowInfo += "[" + srcHost.getIp().toSimpleString() + "]-[" + dstHost.getIp().toSimpleString() 
				+ "](" + getAssignedThroughput() + "/" + requiredThroughput + ")";
		
		return flowInfo;
	}
	
	// Assigned
	boolean installed = false;
	public boolean isInstalled(){
		return this.installed;
	}
	
	// Build completed
	public boolean buildIsReady(){
		if(!this.mainPath.getvPath().isBuild()){
			this.log.info("{}`s virtual path is not ready", mainPath.getPathInfo());
			return false;
		}
		if(!this.mainPath.isBuild()){
			this.log.info("{}`s physical path is not ready", mainPath.getPathInfo());
			return false;
		}
		return true;
	}
	
	// Path
	boolean mainPathOn = true;
	boolean recoveryPathOn = false;
	boolean subPathOn = false;
	
	public boolean setMainPath(PhysicalPath pPath){
		mainPath = pPath;
		pPath.setPhysicalFlow(this);
		return true;
	}
	public PhysicalPath getMainPath(){
		return this.mainPath;
	}
	public PhysicalPath getRecoveryPath(){
		return this.recoveryPath;
	}
	public PhysicalPath getSubPath(){
		return this.subPathList.get(0);
	}
	public Node getSrcSwitch(){
		return this.srcSwitch;
	}
	
	public boolean addSubPath(PhysicalPath pPath){
		if(!subPathList.contains(pPath)){
			subPathList.add(pPath);
			pPath.setPhysicalFlow(this);
			return true;
		}
		return false;
	}
	public boolean setRecoveryPath(PhysicalPath pPath){
		recoveryPath = pPath;
		pPath.setPhysicalFlow(this);
		return true;
	}
	public List<PhysicalPath> getSubPaths(){
		return subPathList;
	}
	public boolean setSrcSwitch(Node srcSwitchMessage){
		this.srcSwitch = srcSwitchMessage;
		return true;
	}
	
	// Host
	public boolean setHostInfo(Host srcHost, Host dstHost){
		this.srcHost = srcHost;
		this.dstHost = dstHost;
		if(srcHost == null){
			return false;
		}
		
		return true;
	}
	
	// Throguhput
	public boolean setRequiredThroughput(){
    	if(srcHost.getIp().toSimpleString().equals("192.0.0.1")){
    		this.requiredThroughput = 150;
    	}else if(srcHost.getIp().toSimpleString().equals("192.0.0.2")){
    		this.requiredThroughput = 5;
    	}else if(srcHost.getIp().toSimpleString().equals("192.0.0.3")){
    		this.requiredThroughput = 50;
    	}else if(srcHost.getIp().toSimpleString().equals("192.0.0.4")){
    		this.requiredThroughput = 5;
    	}
    	else{
    		this.requiredThroughput = 400; 
    	}
    	this.log.info("srcHost is {}. requiredThroughput: {}", srcHost.getIp().toSimpleString(), requiredThroughput);
    	return true;
	}
	public int getRequiredThroughput(){
		return this.requiredThroughput;
	}
	public int getAssignedThroughput(){
		int assignedThroguhput = 0;
		assignedThroguhput += this.mainPath.getAssignedThroughput();
		for(PhysicalPath subPath: this.getSubPaths()){
			assignedThroguhput += subPath.getAssignedThroughput();
		}
		return assignedThroguhput;
	}
	private static int findMinLinkCapacity(PhysicalPath path){
		int minCapacity = 987654321;
		List<PhysicalLink> links = path.calculateCorrespondLinks();
		//find minimum link capacity of this path
		for(PhysicalLink link: links){
			int linkCapacity = link.getMaxCapacity();
			minCapacity = minCapacity > linkCapacity ? linkCapacity : minCapacity;
		}
		return minCapacity;
	}
	
	// Functions
	private boolean adjustBandwidth(List<Entry<PhysicalPath, Integer>> overlapMainPaths) throws LinkMappingException{
		
		int minCapacity = findMinLinkCapacity(this.mainPath);
		
		// Assume that all the overlapped paths pass by the minimum capacity link of this.mainPath
		// It will be supplemented by installing sub paths;
		int sumRT = 0; //sum of required flow throughput of all the overlapped paths
		for(Entry<PhysicalPath, Integer> path: overlapMainPaths){
			PhysicalPath pPath = path.getKey();
			int requiredThroughput = pPath.getPhysicalFlow().getRequiredThroughput();
			sumRT += requiredThroughput;
		}
		log.info("Sum RT: {}, minCapacity: {}", sumRT, minCapacity);
		
		for(Entry<PhysicalPath, Integer> path: overlapMainPaths){
			PhysicalPath mPath = path.getKey();
			int requiredThroughput = mPath.getPhysicalFlow().getRequiredThroughput();
			int adjustedRT; //adjusted required throughput (A result of this function)
			// Divide the bandwidth of the minimum capacity link in proportional to the required throughput of each flow
			adjustedRT = (int)((float)minCapacity / (float)sumRT * (float)requiredThroughput);
			
			log.info("{} adjustedRT: {}", mPath.getPathInfo(), adjustedRT);
			mPath.setBandwidth(adjustedRT); // Adjust each main path`s assigned throughput
			mPath.assignToLinks(); // Apply adjusted throughput to each path
			mPath.showBandwidthInfo(); // to debug
		}
		
		return true;
	}
	
	private static boolean checkPathEnough(PhysicalPath path, int remainThroughput){ // is prior to assignBandwidth()
    	List<PhysicalLink> links = path.calculateCorrespondLinks();
    	
    	//only check outlink because outlink.avail is equal to inlink.avail
    	log.info("Flow {} requiredThroughput is {}", path.getPhysicalFlow().getPhysicalFlowInfo() ,remainThroughput);
    	for(PhysicalLink link: links){
    		if(link.getAvailBandwidth() < remainThroughput){
    			log.info("path {} is not enough at {}", path.getPathInfo(), link.toString());
        		return false; 
    		}
    	}
    	
    	return true;
    }
	
	// Assign Paths(): Assign main paths and calculate sub paths
	synchronized public boolean assignPaths() throws LinkMappingException, IndexOutOfBoundException{
		// Version for Single_Path (190620)
		// If you want a single_path, set the "isSinglePath" to true
		Boolean isSinglePath = true;
		
		// Valid check: virtual path is created && main physical path is created?
		if(this.buildIsReady() == false){
			return false;
		}
		//
        this.setHostInfo(mainPath.getSrcHost(), mainPath.getDstHost());
        
        // 0. VN manager inputs desired throughput
        // Now, the demand is determined based on src_host_IP 
        this.setRequiredThroughput(); 
        
        // Bandwidth of this flow is already installed enough?
		if(mainPath.isAssigned() && this.getAssignedThroughput() == this.getRequiredThroughput()){
			// Already installed enough -> Return
			this.log.info("{} is already assigned enough", mainPath.getPathInfo());
			mainPath.showBandwidthInfo();
			return true; 
		}
		
		// 1. Check whether the main path satisfies required throughput.
		if(isSinglePath || checkPathEnough(this.mainPath, this.getRequiredThroughput())){
			// Main path satisfies required throughput
			// Additional path is not required
			this.log.info("{}: Main path satisfy the entire bandwidth.", mainPath.getPathInfo());
			
			// Set entire required throughput to main path
			mainPath.setBandwidth(this.requiredThroughput);
			// Assign the path to links 
			mainPath.assignToLinks();
			mainPath.showBandwidthInfo(); // to debug
		}
		else{
			// Calculate additional paths
			this.log.info("{}: Additional path is needed", mainPath.getPathInfo());
			
			// 2. If the cause is other tenant`s secondary path (sub or recovery path), delete the secondary paths.
			// List<Path, Requirement>
			//List<PhysicalPath> victimRecoveryPaths = new LinkedList<PhysicalPath>();
			List<PhysicalPath> deletedSubPaths = new LinkedList<PhysicalPath>();
			List<Entry<PhysicalPath, Integer>> overlappedSubPaths = new LinkedList<Entry<PhysicalPath, Integer>>();
			
			// Find overlapped secondary paths which are sorted in descending order (based on assigned bandwidth on the path)
			overlappedSubPaths = mainPath.findOverlappedSubPaths();
			this.log.info("Nuumber of overlapped paths is {} for {} ", overlappedSubPaths.size(), mainPath.getPathInfo());
			
			// Delete overlapped secondary paths until link capacity is secured
			int remainingRequired = requiredThroughput; 
			remainingRequired = deleteOverlappedPaths(overlappedSubPaths, deletedSubPaths, remainingRequired);
			
			// 3. If the cause is other tenant`s main path (or The throughput was not sufficiently secured yet), 
			// If even though every overlap secondary paths is deleted the link`s available throughput is not enough,
			// 1) Find overlapped other tenant`s main paths and
			// 2) Adjust these main paths and
			
			// 4. Install or Reinstall sub path for the flow of the main paths 
			//    (Because of 
			//     a. The adjusted value of the allocated throughput OR
			//     b. A lack of the capacity of the physical link consisting the main path)
			//
			
			// Add a <main path, flow required throughput> to overlap array
			List<Entry<PhysicalPath, Integer>> overlappedMainPaths = new LinkedList<Entry<PhysicalPath, Integer>>();
			overlappedMainPaths.add(new AbstractMap.SimpleEntry<>(mainPath, this.requiredThroughput)); // Add Me
			
			if(remainingRequired > 0){
				// 1) Find overlap main paths which is sorted in descending order (based on required throughput on the flow)
				overlappedMainPaths.addAll(mainPath.findOverlappedMainPaths()); // add overlapped main paths
				// 2) Adjusts each main path`s assigned throughput value in proportion to it's requiredThroughput.
				adjustBandwidth(overlappedMainPaths);
				// 4. Calculate and assign additional paths, if needed
				assignSubPaths(overlappedMainPaths);
				this.log.info("{} is overlapped with {} main paths", mainPath.getPathInfo(), overlappedMainPaths.size()-1); //except Me!
			}
			// (x) 5. Reinstall the deleted paths: Recovery path always exist in FAVE
			// reinstallVictimRecoveryPaths(victimRecoveryPaths);
		}
		
		// 6. Build a recovery path.
		//buildRecoveryPath(mainPath);

		// 7. Create FlowMod and GroupMod Message.
		createFlowMessages();
		
		// 8. Install all path of a physical flow to physical switches.
		// In other words, Send FlowMod, GroupMod Messages.
		installFlowToPhysicalNetwork();
		
		return true;
	}
	
	private boolean createFlowMessages(){
		if(this.mainPath != null & this.mainPath.isValid()){
			mainPathOn = true;
		}
//		if(this.recoveryPath != null && this.recoveryPath.isValid()){
//			recoveryPathOn = true;
//		}
		if(!this.subPathList.isEmpty() && this.subPathList.get(0).isValid() ){
			subPathOn = true;
		}
		
		// The FlowMod and GroupMod of intermediates and destination switches is independent.
		// So, we should handle the FlowMod and GroupMod of source switch according to each case.
		// In addition, for easy implementation, 
		// I assume that the group table is always used even in the case of the only-TP case.
		
		// The Match/Action for FlowMod messages is same in all the cases
		// So, Creating FlowMod is done equally by createSrcFlowModMessages() function.
		createSrcFlowModMessages();
		// Therefore, the Group/Bucket for GroupMod will be created differently according to each case by bellow functions
		
		// 1. TP, SP, AP on
		if(subPathOn && recoveryPathOn && mainPathOn){
			createSrcMessagesRecSub();
		}
		// 2. TP, SP on
		else if(recoveryPathOn && mainPathOn){
			createSrcMessagesRec();
		}
		// 3. TP, AP on
		// Actually, this case is not need based on the design.
		// That is because SP is always provided
		// (190306) It is used for ML_TALON 
		else if(subPathOn && mainPathOn){
			createSrcMessagesSub();
		}
		// 4 . TP on
		else if(mainPathOn){
			createSrcMessages();
		}
		
		this.srcSwitch.setSwitch(this.mainPath.getSrcSwitch().getSwitch());
		return true;
	}
	
	// Common logic
	private boolean createSrcFlowModMessages(){
		Node mainNode = mainPath.getSrcSwitch();
		// MainPath Match
		Match mainPathMatch = mainNode.getMplsFlowMod().getMatch().createBuilder().build();
		// MainPath Actions
		List<OFAction> mainPathActions = mainNode.getMplsFlowMod().getActions();
		
		// ----- Match / Action -----
		// New Match: It is same to the original one
		Match newMatch = mainPathMatch.createBuilder().build();
		// New Action: Group(main)
		List<OFAction> newActions = new LinkedList<OFAction>();
		List<OFInstruction> newInstructions = new LinkedList<OFInstruction>();
		OFActionGroup actionToGroup = factory.actions().buildGroup()
        		.setGroup(OFGroup.of(mainPath.getLabelValue()))
                .build();
		OFInstruction inst = factory.instructions().buildMeter()
				.setMeterId(mainPath.getFlowID())
				.build();
		newActions.add(actionToGroup);
		OFInstruction inst2 = factory.instructions().applyActions(newActions);
		newInstructions.add(inst); //gijun*/
		newInstructions.add(inst2);
		// Create FlowMod message
        OFFlowMod newFlowMod = mainNode.getMplsFlowMod().createBuilder()
        		.setMatch(newMatch).setInstructions(newInstructions).build();
        this.srcSwitch.setMplsFlowMod(newFlowMod);
        
        // Set newFlowMod to mainPath's srcNode because of synchronization of the flowMod messages
        // this.mainPath.getSrcSwitch().setMplsFlowMod(newFlowMod);
        // --------------------------
        return true;
	}
	
	private boolean createSrcMessages(){
		log.info("createSrcMessages() for {}", this.getPhysicalFlowInfo());
		Node mainNode = mainPath.getSrcSwitch();
		
		// MainPath Match
		Match mainPathMatch = mainNode.getMplsFlowMod().getMatch().createBuilder().build();
		// MainPath Actions
		List<OFAction> mainPathActions = mainNode.getMplsFlowMod().getActions();

        // ----- Group / Bucket -----
	    // New Bucket1: is same to actions of "Main path"
        ArrayList<OFBucket> newBuckets = new ArrayList<OFBucket>();
	    newBuckets.add(factory.buildBucket()
	            .setActions(mainPathActions)
	            .setWatchGroup(OFGroup.ANY)
	            .setWatchPort(OFPort.ANY)
	            .setWeight(5) // It doesn`t matter
	            .build());
	    // Create GroupMod message (Main<select>)
        OFGroupMod newGroupAdd = factory.buildGroupAdd()
        	    .setGroup(OFGroup.of(mainPath.getLabelValue()))
        	    .setGroupType(OFGroupType.SELECT)
        	    .setBuckets(newBuckets)
        	    .build();
        OFGroupMod newGroupMod = factory.buildGroupModify()
        	    .setGroup(OFGroup.of(mainPath.getLabelValue()))
        	    .setGroupType(OFGroupType.SELECT)
        	    .setBuckets(newBuckets)
        	    .build();
        // Create MeterAdd message
        //Set<OFMeterFlags> flags = new HashSet<>(asList(OFMeterFlags.KBPS, OFMeterFlags.BURST));
        OFMeterBand mb = factory.meterBands()
                .buildDrop()
				.setRate(mainPath.getAssignedThroughput() * 1024)
				.setBurstSize(mainPath.getAssignedThroughput() * 1024)
				.build();
		ArrayList<OFMeterBand> mbl = new ArrayList<OFMeterBand>();
		mbl.add(mb);
		OFMeterMod mm = factory.buildMeterMod()
				.setMeters(mbl)
				.setMeterId(mainPath.getFlowID())
				.setCommand(0) 
				.setFlags(1)
				.build();
		// Create MeterMod message
		OFMeterBand mb2 = factory.meterBands()
                .buildDrop()
				.setRate(mainPath.getAssignedThroughput() * 1024)
				.setBurstSize(mainPath.getAssignedThroughput() * 1024)
				.build();
		ArrayList<OFMeterBand> mbl2 = new ArrayList<OFMeterBand>();
		mbl2.add(mb2);
		OFMeterMod mm2 = factory.buildMeterMod()
				.setMeters(mbl2)
				.setMeterId(mainPath.getFlowID())
				.setCommand(0) 
				.setFlags(1)
				.build();

		this.srcSwitch.setMplsMeterAdd(mm);
		this.srcSwitch.setMplsMeterMod(mm2);
        this.srcSwitch.setMplsGroupAdd(newGroupAdd);
        this.srcSwitch.setMplsGroupMod(newGroupMod);
        // --------------------------
		return true;
	}
	
	private boolean createSrcMessagesRec(){
		log.info("createSrcMessagesRec() for {}", this.getPhysicalFlowInfo());
		Node mainNode = mainPath.getSrcSwitch();
		Node recoveryNode = recoveryPath.getSrcSwitch();
		
		// MainPath Actions
		List<OFAction> mainPathActions = mainNode.getMplsFlowMod().getActions();
		// RecoveryPath Actions
		List<OFAction> recoveryPathActions = recoveryNode.getMplsFlowMod().getActions();
		
		// ----- Group / Bucket -----
	    // New Bucket1: is same to actions of "Main path", watch "Main port"
        ArrayList<OFBucket> newBuckets = new ArrayList<OFBucket>();
	    newBuckets.add(factory.buildBucket()
	            .setActions(mainPathActions)
	            .setWatchGroup(OFGroup.ANY)
	            .setWatchPort(OFPort.of(mainNode.getOutPort().getPortNumber()))
	            .build());
	    // New Bucket2: is same to actions of "Recovery path", watch "Recovery port"
	    newBuckets.add(factory.buildBucket()
	            .setActions(recoveryPathActions)
	            .setWatchGroup(OFGroup.ANY)
	            .setWatchPort(OFPort.of(recoveryNode.getOutPort().getPortNumber()))
	            .build());
	    // Create GroupMod message (main<ff>)
        OFGroupMod newGroupAdd = factory.buildGroupAdd()
        	    .setGroup(OFGroup.of(mainPath.getLabelValue()))
        	    .setGroupType(OFGroupType.FF)
        	    .setBuckets(newBuckets)
        	    .build();
        OFGroupMod newGroupMod = factory.buildGroupModify()
        	    .setGroup(OFGroup.of(mainPath.getLabelValue()))
        	    .setGroupType(OFGroupType.FF)
        	    .setBuckets(newBuckets)
        	    .build();
        this.srcSwitch.setMplsGroupAdd(newGroupAdd);
        this.srcSwitch.setMplsGroupMod(newGroupMod);
        // --------------------------
        return true;
	}
	
	public static int gcd(int a, int b) {
	    while (b != 0) {
	      int temp = a % b;
	      a = b;
	      b = temp;
	    }
	    return Math.abs(a);
	  }
	
	private boolean createSrcMessagesSub(){
		log.info("createSrcMessagesSub() for {}", this.getPhysicalFlowInfo());
		Node mainNode = mainPath.getSrcSwitch();
		Node subNode = subPathList.get(0).getSrcSwitch();
		
		int weightGcd = gcd(mainPath.getAssignedThroughput(), subPathList.get(0).getAssignedThroughput());
		//int mainPathWeight = mainPath.getAssignedThroughput() / weightGcd;
		//int subPathWeight = subPathList.get(0).getAssignedThroughput() / weightGcd;
		
		int mainPathWeight = 2;
		int subPathWeight = 2;
		log.info("main: {}, sub: {}, gcd: {}, mainW: {}, subW: {}", 
				mainPath.getAssignedThroughput(), subPathList.get(0).getAssignedThroughput(),
				weightGcd, mainPathWeight, subPathWeight);
		
		// MainPath Actions
		List<OFAction> mainPathActions = mainNode.getMplsFlowMod().getActions();
		// SubPath Actions
		List<OFAction> subPathActions = subNode.getMplsFlowMod().getActions();
		List<OFAction> subPathActions1212 = this.getSrcSwitch().getMplsFlowMod().getActions();
		//Match mainMatch = mainNode.getMplsFlowMod().getMatch(); //match
		
		
		// ----- Group / Bucket -----
	    // New Bucket1: is same to actions of "Main path"
        ArrayList<OFBucket> newBuckets = new ArrayList<OFBucket>();
	    newBuckets.add(factory.buildBucket()
	            .setActions(mainPathActions)
	            .setWatchGroup(OFGroup.ANY)
	            .setWatchPort(OFPort.ANY)
	            .setWeight(mainPathWeight)
	            .build());
	    // New Bucket2: is same to actions of "Sub path"
	    newBuckets.add(factory.buildBucket()
	            .setActions(subPathActions)
	            .setWatchGroup(OFGroup.ANY)
	            .setWatchPort(OFPort.ANY)
	            .setWeight(subPathWeight)
	            .build());
	    // Create GroupMod message (main<select>)
        OFGroupMod newGroupAdd = factory.buildGroupAdd()
        	    .setGroup(OFGroup.of(mainPath.getLabelValue()))
        	    .setGroupType(OFGroupType.SELECT)
        	    .setBuckets(newBuckets)
        	    .build();
        OFGroupMod newGroupMod = factory.buildGroupModify()
        	    .setGroup(OFGroup.of(mainPath.getLabelValue()))
        	    .setGroupType(OFGroupType.SELECT)
        	    .setBuckets(newBuckets)
        	    .build();
        // Create MeterAdd message
        OFMeterBand mb = factory.meterBands()
                .buildDrop()
				.setRate(mainPath.getAssignedThroughput() * 1024)
				.setBurstSize(mainPath.getAssignedThroughput() * 1024)
				.build();
		ArrayList<OFMeterBand> mbl = new ArrayList<OFMeterBand>();
		mbl.add(mb);
		OFMeterMod mm = factory.buildMeterMod()
				.setMeters(mbl)
				.setMeterId(mainPath.getFlowID())
				.setCommand(0) 
				.setFlags(1)
				.build();
		// Create MeterMod message
		OFMeterBand mb2 = factory.meterBands()
                .buildDrop()
				.setRate(mainPath.getAssignedThroughput() * 1024)
				.setBurstSize(mainPath.getAssignedThroughput() * 1024)
				.build();
		ArrayList<OFMeterBand> mbl2 = new ArrayList<OFMeterBand>();
		mbl2.add(mb2);
		OFMeterMod mm2 = factory.buildMeterMod()
				.setMeters(mbl2)
				.setMeterId(mainPath.getFlowID())
				.setCommand(0) 
				.setFlags(1)
				.build();

		this.srcSwitch.setMplsMeterAdd(mm);
		this.srcSwitch.setMplsMeterMod(mm2);
        this.srcSwitch.setMplsGroupAdd(newGroupAdd);
        this.srcSwitch.setMplsGroupMod(newGroupMod);
        // --------------------------
		return true;
	}
	
	private boolean createSrcMessagesRecSub(){
		log.info("createSrcMessagesRecSub() for {}", this.getPhysicalFlowInfo());
		Node mainNode = mainPath.getSrcSwitch();
		Node recoveryNode = recoveryPath.getSrcSwitch();
		Node subNode = subPathList.get(0).getSrcSwitch();
		
		// MainPath Actions
		List<OFAction> mainPathActions = mainNode.getMplsFlowMod().getActions();
		// SubPath Actions
		List<OFAction> subPathActions = subNode.getMplsFlowMod().getActions();
		// RecoveryPath Actions
		List<OFAction> recoveryPathActions = recoveryNode.getMplsFlowMod().getActions();
		
		// ----- Group / Bucket -----
		// Group 1
	    // New Bucket1-1: Group(recovery)
		List<OFAction> bucketActionsGroupRecovery = new LinkedList<OFAction>();
	    OFActionGroup actionToGroupRecovery = factory.actions().buildGroup()
        		.setGroup(OFGroup.of(recoveryPath.getLabelValue()))
                .build();
	    bucketActionsGroupRecovery.add(actionToGroupRecovery); // to group(recovery)
        ArrayList<OFBucket> newBuckets = new ArrayList<OFBucket>();
	    newBuckets.add(factory.buildBucket()
	            .setActions(bucketActionsGroupRecovery)
	            .setWatchGroup(OFGroup.ANY)
	            .setWatchPort(OFPort.ANY)
	            .setWeight(5)
	            .build());
	    
	    // New Bucket1-2: is same to actions of "Sub path"
	    newBuckets.add(factory.buildBucket()
	            .setActions(subPathActions)
	            .setWatchGroup(OFGroup.ANY)
	            .setWatchPort(OFPort.ANY)
	            .setWeight(5)
	            .build());
	    // Create GroupMod message (main<select>)
        OFGroupMod newGroupAdd = factory.buildGroupAdd()
        	    .setGroup(OFGroup.of(mainPath.getLabelValue()))
        	    .setGroupType(OFGroupType.SELECT)
        	    .setBuckets(newBuckets)
        	    .build();
        OFGroupMod newGroupMod = factory.buildGroupModify()
        	    .setGroup(OFGroup.of(mainPath.getLabelValue()))
        	    .setGroupType(OFGroupType.SELECT)
        	    .setBuckets(newBuckets)
        	    .build();
        this.srcSwitch.setMplsGroupAdd(newGroupAdd);
        this.srcSwitch.setMplsGroupMod(newGroupMod);
        
        // Group 2
        // New Bucket2-1: is same to actions of "Main path", watch "Main port"
        ArrayList<OFBucket> newBuckets2 = new ArrayList<OFBucket>();
	    newBuckets2.add(factory.buildBucket()
	            .setActions(mainPathActions)
	            .setWatchGroup(OFGroup.ANY)
	            .setWatchPort(OFPort.of(mainNode.getOutPort().getPortNumber()))
	            .build());
	    // New Bucket2-2: is same to actions of "Recovery path", watch "Recovery port"
	    newBuckets2.add(factory.buildBucket()
	            .setActions(recoveryPathActions)
	            .setWatchGroup(OFGroup.ANY)
	            .setWatchPort(OFPort.of(recoveryNode.getOutPort().getPortNumber()))
	            .build());
	    // Create GroupMod message (main<ff>)
        OFGroupMod newGroup2Add = factory.buildGroupAdd()
        	    .setGroup(OFGroup.of(recoveryPath.getLabelValue()))
        	    .setGroupType(OFGroupType.FF)
        	    .setBuckets(newBuckets2)
        	    .build();
        OFGroupMod newGroup2Mod = factory.buildGroupModify()
        	    .setGroup(OFGroup.of(recoveryPath.getLabelValue()))
        	    .setGroupType(OFGroupType.FF)
        	    .setBuckets(newBuckets2)
        	    .build();
        this.srcSwitch.setMplsGroup2Add(newGroup2Add);
        this.srcSwitch.setMplsGroup2Mod(newGroup2Mod);
        // --------------------------
        return true;
	}
	
	private boolean installFlowToPhysicalNetwork() throws LinkMappingException, IndexOutOfBoundException{
		if(this.mainPathOn){
			installPathToPhysicalSwitches(this.mainPath);
		}
		if(this.recoveryPathOn){
			installPathToPhysicalSwitches(this.recoveryPath);
		}
		if(this.subPathOn){
			installPathToPhysicalSwitches(this.subPathList.get(0));
		}
		
		this.installed = true;
		return true;
	}
	
	private boolean installPathToPhysicalSwitches(PhysicalPath path) throws LinkMappingException{
		if(path.isBuild()){
			log.info("Flow {}: Path {} is installed", this.getPhysicalFlowInfo(), path.getPathInfo());
			path.sendSouth();
			path.assignToLinks();
			path.showBandwidthInfo(); // to debug
    	}
		else{
			log.info("Flow {}: Path {} fail to be installed", this.getPhysicalFlowInfo(), path.getPathInfo());
		}
		
		return true;
	}
	
	private boolean reinstallVictimRecoveryPaths(List<PhysicalPath> victimRecoveryPaths) throws LinkMappingException, IndexOutOfBoundException{
		for(PhysicalPath recoveryPath: victimRecoveryPaths){
			PhysicalPath mainPath = recoveryPath.getPhysicalFlow().getMainPath();
			buildRecoveryPath(mainPath);
			recoveryPath.getPhysicalFlow().installFlowToPhysicalNetwork();
			log.info("Reinstall: {}", recoveryPath.getPathInfo());
		}
		return true;
	}
	
	private boolean buildRecoveryPath(PhysicalPath mainPath) throws IndexOutOfBoundException, LinkMappingException{
		int assignedThroughput = mainPath.getAssignedThroughput();
		PhysicalPath newPath = PhysicalPathBuilder.getInstance().createSubPhysicalPath(mainPath.getvPath(), mainPath, PathType.RECOVERY);
		newPath.setBandwidth(assignedThroughput);
		newPath.assignToLinks(); // Assign throughput to a path
		newPath.showBandwidthInfo(); // to debug
		
		log.info("Recovery path {} is built for Mainpath {}", newPath.getPathInfo(), mainPath.getPathInfo());
		return true;
	}
	
	
	private int buildSubPath(PhysicalPath mainPath, int remainingThroughput) throws LinkMappingException, IndexOutOfBoundException{
		PhysicalPath newPath = PhysicalPathBuilder.getInstance().createSubPhysicalPath(mainPath.getvPath(), mainPath, PathType.SUB);
		int assignedThroughput = remainingThroughput;
		// Comment: Assume that the number of sub path is only one
//		if(!checkPathEnough(newpPath, remainThroughput)){
//			assignedThroughput = findMinLinkCapacity(newpPath);
//		}
		
		newPath.setBandwidth(assignedThroughput);
		newPath.assignToLinks(); // Assign throughput to a path
		newPath.showBandwidthInfo(); // to debug
		
		remainingThroughput -= assignedThroughput;
		log.info("Sub path {} is built for Mainpath {}", newPath.getPathInfo(), mainPath.getPathInfo());
		
		return remainingThroughput;
	}
	
	private boolean assignSubPaths(List<Entry<PhysicalPath, Integer>> overlapMainPaths) throws IndexOutOfBoundException, LinkMappingException{	
		int maxNumSubPaths = 1;
		log.info("buildSubPaths() IN");
		for(Entry<PhysicalPath, Integer> overlapMainPath: overlapMainPaths){
			PhysicalPath mainPath = overlapMainPath.getKey();
    		int requiredThroughput = mainPath.getPhysicalFlow().getRequiredThroughput();
    		int mainBandwidth = mainPath.getAssignedThroughput();
    		int remainingBandwidth = requiredThroughput - mainBandwidth;
    		log.info("buildSubPaths() requiredThroughput: {}, mainAssignedThroughput: {}, remainThroughput: {}, Path: {}", 
    				requiredThroughput, mainBandwidth, remainingBandwidth, mainPath);
    		
    		while(remainingBandwidth > 0 && maxNumSubPaths > (mainPath.getPhysicalFlow().getSubPaths().size()) ){
    			remainingBandwidth = buildSubPath(mainPath, remainingBandwidth);
    		}
    	}
		return true;
	}

	private int deleteOverlappedPaths(List<Entry<PhysicalPath, Integer>> candidateSubPaths,
			List<PhysicalPath> victimSubPaths , int remainingRequired) throws LinkMappingException{
		// Delete overlapped sub paths 
		// until the link`s available throughput is enough
		for(Entry<PhysicalPath, Integer> candidate: candidateSubPaths){
			if(remainingRequired > 0){
				PhysicalPath victimPath = candidate.getKey();
				int deletedPathBandwidth = candidate.getValue();
				victimSubPaths.add(victimPath);
				victimPath.deleteSecondaryPath();
				this.log.info("{} is deleted", victimPath.getPathInfo());
				remainingRequired -= deletedPathBandwidth;
			}
		}
		return remainingRequired;
	}
}

