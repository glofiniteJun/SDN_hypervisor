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

import net.onrc.openvirtex.elements.OVXMap;
import net.onrc.openvirtex.elements.datapath.OVXSwitch;
import net.onrc.openvirtex.elements.datapath.PhysicalSwitch;
import net.onrc.openvirtex.elements.network.OVXNetwork;
import net.onrc.openvirtex.elements.port.OVXPort;
import net.onrc.openvirtex.exceptions.IndexOutOfBoundException;
import net.onrc.openvirtex.exceptions.NetworkMappingException;
import net.onrc.openvirtex.protocol.OVXMatch;
import net.onrc.openvirtex.routing.ShortestPath;
import net.onrc.openvirtex.services.path.PathUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.match.MatchField;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class VirtualPathBuilder {
    private static VirtualPathBuilder instance;
    private static Logger log = LogManager.getLogger(VirtualPathBuilder.class.getName());

    private static ConcurrentHashMap<Integer, VirtualPath> PathIDvirtualPathMap;

    private VirtualPathBuilder() {

        this.PathIDvirtualPathMap = new ConcurrentHashMap<>();
    }

    public synchronized static VirtualPathBuilder getInstance() {
        if (VirtualPathBuilder.instance == null) {
            log.info("Starting VirtualPathBuilder");

            VirtualPathBuilder.instance = new VirtualPathBuilder();
        }
        return VirtualPathBuilder.instance;
    }

    public Collection<VirtualPath> getVirtualPaths() {
        return PathIDvirtualPathMap.values();
    }

    //only for 1:1 = OVXSwitch:PhysicalSwitch Mapping. Additional implementation is required 1:N Mapping
    public synchronized VirtualPath buildVirtualPath(OVXMatch ovxMatch, OFFlowMod oriFlowMod) {
        int pathID = PathUtil.getInstance().makePathID(ovxMatch.getTenantId(), ovxMatch.getFlowId());
        VirtualPath vPath = PathIDvirtualPathMap.get(pathID);
        if(vPath == null){
            vPath = new VirtualPath(ovxMatch.getFlowId(), ovxMatch.getTenantId(), pathID);

            PathIDvirtualPathMap.put(pathID, vPath);
            
            log.info("Start building VirtualPath ID [{}]", pathID);
        }else{
            if(vPath.isMigrated()) {
                //log.info("VirtualPath ID [{}] is migrated {}", ovxMatch.getFlowId(), oriFlowMod.toString());
                return null;
            }
        }

        if(vPath.isBuild()) {
            log.info("VirtualPath ID [{}] is already built", pathID);
            
            //log.info("Create New VirtualPath, ID: [{}]", pathID);
        }else{
            //log.info("VirtualPath ID [{}] is not built", ovxMatch.getFlowId());
            vPath.buildVirtualPath(ovxMatch.getSwitchType(), oriFlowMod, ovxMatch.getOVXSwitch());
        }

        return vPath;
    }

    public synchronized OFFlowMod removeVirtualPath(OFFlowMod delFlowMod, OVXMatch ovxMatch) {
        OVXNetwork vnet = null;
        Integer pathId = 0;
        int flowId = 0;
        try {
            ovxMatch.setTenantId(ovxMatch.getOVXSwitch().getTenantId());
            vnet = ovxMatch.getOVXSwitch().getMap().getVirtualNetwork(ovxMatch.getTenantId());
        } catch (NetworkMappingException e) {
            e.printStackTrace();
        }
        
       
        try {
            flowId =  vnet.getFlowManager().getFlowValues(
                    delFlowMod.getMatch().get(MatchField.ETH_SRC).getBytes(),
                    delFlowMod.getMatch().get(MatchField.ETH_DST).getBytes());

            ovxMatch.setFlowId(flowId);
        } catch (IndexOutOfBoundException e) {
            e.printStackTrace();
        }

        //log.info("FlowID {}", flowId);
        pathId = PathUtil.getInstance().makePathID(ovxMatch.getTenantId(), ovxMatch.getFlowId());

        VirtualPath vPath = PathIDvirtualPathMap.get(pathId);
        if(vPath == null){
            log.info("VirtualPath ID [{}] does not exist", pathId);
            return null;
        }else{
            OFFlowMod ofFlowMod = vPath.removeVirtualPath(delFlowMod, ovxMatch);
            if(vPath.isRemoveVirtualPath()) {
                PathIDvirtualPathMap.remove(pathId);
                log.info("VirtualPath ID [{}] is removed", pathId);
            }
            return ofFlowMod;
        }
    }

    public VirtualPath getVirtualPath(int pathID) {
        return this.PathIDvirtualPathMap.get(pathID);
    }

    public static ConcurrentHashMap<Integer, VirtualPath> getPathIDvirtualPathMap() {
        return PathIDvirtualPathMap;
    }

}
