/*******************************************************************************
 * Copyright 2014 Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ****************************************************************************
 * Libera HyperVisor development based OpenVirteX for SDN 2.0
 *
 *   OpenFlow Version Up with OpenFlowj
 *
 * This is updated by Libera Project team in Korea University
 *
 * Author: Seong-Mun Kim (bebecry@gmail.com)
 ******************************************************************************/
package net.onrc.openvirtex.elements.link;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import net.onrc.openvirtex.api.service.handlers.TenantHandler;
import net.onrc.openvirtex.elements.Persistable;
import net.onrc.openvirtex.elements.datapath.PhysicalSwitch;
import net.onrc.openvirtex.elements.port.PhysicalPort;
import net.onrc.openvirtex.services.path.physicalflow.PhysicalFlow;
import net.onrc.openvirtex.services.path.physicalpath.PhysicalPath;;

/**
 * The Class PhysicalLink.
 *
 */
public class PhysicalLink extends Link<PhysicalPort, PhysicalSwitch> implements
        Persistable, Comparable<PhysicalLink> {

    private static AtomicInteger linkIds = new AtomicInteger(0);

    @SerializedName("linkId")
    @Expose
    private Integer linkId = null;

    private Integer maximumCapacity;
    //private Integer primaryBandwidth; 
    //private Integer subBandwidth; --> get from calculating correspond paths
    
    private Set<PhysicalPath> ppaths= Collections.synchronizedSet(new HashSet<PhysicalPath>());

    /**
     * Instantiates a new physical link.
     *
     * @param srcPort
     *            the source port
     * @param dstPort
     *            the destination port
     */
    public PhysicalLink(final PhysicalPort srcPort, final PhysicalPort dstPort) {
        super(srcPort, dstPort);
        srcPort.setOutLink(this);
        dstPort.setInLink(this);
        this.linkId = PhysicalLink.linkIds.getAndIncrement();
        
        maximumCapacity = 200;
    }

    public Integer getLinkId() {
        return linkId;
    }

    @Override
    public void unregister() {
        this.getSrcSwitch().getMap().removePhysicalLink(this);
        srcPort.setOutLink(null);
        dstPort.setInLink(null);
    }

    @Override
    public Map<String, Object> getDBObject() {
        Map<String, Object> dbObject = super.getDBObject();
        dbObject.put(TenantHandler.LINK, this.linkId);
        return dbObject;
    }

    public void setLinkId(Integer id) {
        this.linkId = id;
    }

    @Override
    public int compareTo(PhysicalLink o) {
        Long sum1 = this.getSrcSwitch().getSwitchId()
                + this.getSrcPort().getPortNumber();
        Long sum2 = o.getSrcSwitch().getSwitchId()
                + o.getSrcPort().getPortNumber();
        if (sum1 == sum2) {
            return (int) (this.getSrcSwitch().getSwitchId() - o.getSrcSwitch()
                    .getSwitchId());
        } else {
            return (int) (sum1 - sum2);
        }
    }

    
    public String toString() {
        final String srcSwitch = to64BitsForm(this.getSrcSwitch().getSwitchId());
        final String dstSwitch = to64BitsForm(this.getDstSwitch().getSwitchId());
        final short srcPort = this.srcPort.getPortNumber();
        final short dstPort = this.dstPort.getPortNumber();
        final int assignedBamdwidth = getAssignedBandwidth();
        return srcSwitch + "/" + srcPort + "-" + dstSwitch + "/" + dstPort 
        		+ "[" + assignedBamdwidth + "/"+ maximumCapacity + "]" ;
    }
    
    private static String to64BitsForm(long rawNumber){
    	long mask = (long)0xff;
    	String resultStr = 
    			String.format("%d:%d",
    					(rawNumber >> 56 & mask),
    					(rawNumber & mask));
//    			String.format("%d.%d.%d.%d.%d.%d.%d.%d",    
//				 (rawNumber >> 56 & mask),    
//				 (rawNumber >> 48 & mask),
//				 (rawNumber >> 40 & mask),
//				 (rawNumber >> 32 & mask),
//				 (rawNumber >> 24 & mask),
//				 (rawNumber >> 16 & mask),
//				 (rawNumber >> 8 & mask),
//    			 (rawNumber & mask));
    	
    	return resultStr;
    }
    
    public int getMaxCapacity(){
    	return maximumCapacity;
    }
    
    public int getAvailBandwidth(){
    	return maximumCapacity - getAssignedBandwidth();
    }
    public int getAvailPrimaryBandwidth(){
    	return maximumCapacity - getAssignedPrimaryBandwidth();
    }
    public int getAvailSubBandwidth(){
    	return maximumCapacity - getAssignedSubBandwidth();
    }
    
    
    public int getAssignedBandwidth(){
    	return getAssignedPrimaryBandwidth() + getAssignedSubBandwidth();
    }
    
    public int getAssignedPrimaryBandwidth(){
    	int assignedMainThroguhput = 0;
    	for(PhysicalPath pPath: ppaths){
    		if(pPath.isPrimaryPath()){
    			assignedMainThroguhput += pPath.getAssignedThroughput();
    		}
    	}
    	return assignedMainThroguhput;
    }
    
    public int getAssignedSubBandwidth(){
    	int assignedSubThroguhput = 0;
    	for(PhysicalPath pPath: ppaths){
    		if(!pPath.isPrimaryPath()){
    			assignedSubThroguhput += pPath.getAssignedThroughput();
    		}
    	}
    	return assignedSubThroguhput;
    }
    
    /* ==== */
    // Assign to path. Highest function
    public void assignToPath(PhysicalPath ppath){
    	if(!ppaths.contains(ppath)){
    		ppaths.add(ppath);
    	}
    }
    public void unAssignToPath(PhysicalPath ppath){
    	if(ppaths.contains(ppath)){
    		ppaths.remove(ppath);
    	}
    }
   
    // Map paths to link
    public Set<PhysicalPath> getMappedPath(){
    	return this.ppaths;
    }
    public boolean isContained(PhysicalFlow physicalFlow){
    	for(PhysicalPath path: this.ppaths){
    		if(path.getPhysicalFlow().getFlowId() == physicalFlow.getFlowId()){
    			return true;
    		}
    	}
    	return false;
    }
    
}
