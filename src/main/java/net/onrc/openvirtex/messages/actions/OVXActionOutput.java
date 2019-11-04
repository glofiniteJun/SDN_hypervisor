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
package net.onrc.openvirtex.messages.actions;

import net.onrc.openvirtex.elements.datapath.OVXBigSwitch;
import net.onrc.openvirtex.elements.datapath.OVXSwitch;
import net.onrc.openvirtex.elements.link.OVXLink;
import net.onrc.openvirtex.elements.link.OVXLinkUtils;
import net.onrc.openvirtex.elements.network.OVXNetwork;
import net.onrc.openvirtex.elements.port.OVXPort;
import net.onrc.openvirtex.elements.port.PhysicalPort;
import net.onrc.openvirtex.exceptions.*;
import net.onrc.openvirtex.messages.OVXFlowMod;
import net.onrc.openvirtex.messages.OVXPacketIn;
import net.onrc.openvirtex.messages.OVXPacketOut;
import net.onrc.openvirtex.protocol.OVXMatch;
import net.onrc.openvirtex.services.path.SwitchType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;

import org.projectfloodlight.openflow.protocol.action.*;
import org.projectfloodlight.openflow.protocol.match.MatchField;

import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U16;
import org.projectfloodlight.openflow.types.U64;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class OVXActionOutput extends OVXAction implements VirtualizableAction {
    Logger log = LogManager.getLogger(OVXActionOutput.class.getName());

    private OFActionOutput ofActionOutput;

    public OVXActionOutput(OFAction ofAction) {
        super(ofAction);
        this.ofActionOutput = (OFActionOutput)ofAction;
    }

    @Override
    public void virtualize(OVXSwitch sw, List<OFAction> approvedActions, OVXMatch match)
            throws ActionVirtualizationDenied, DroppedMessageException {
        OFFactory ofFactory = OFFactories.getFactory(sw.getOfVersion());
        //this.log.info("virtualize");

        //final OVXPort2 inPort = sw.getPort(match.getMatch().get(MatchField.IN_PORT).getShortPortNumber());

        OVXPort inPort;

        LinkedList<OVXPort> outPortList;

        if(match.getMatch().get(MatchField.IN_PORT) != null) {
            inPort = sw.getPort(match.getMatch().get(MatchField.IN_PORT).getShortPortNumber());

            outPortList = this.fillPortList(
                    match.getMatch().get(MatchField.IN_PORT).getShortPortNumber(),
                    this.ofActionOutput.getPort().getShortPortNumber(),
                    sw
            );
        }else{
            inPort = sw.getPort((short)0);

            outPortList = this.fillPortList(
                    (short)0,
                    this.ofActionOutput.getPort().getShortPortNumber(),
                    sw
            );
        }

        // TODO: handle TABLE output port here
        final OVXNetwork vnet;
        try {
            vnet = sw.getMap().getVirtualNetwork(sw.getTenantId());
        } catch (NetworkMappingException e) {
            log.warn("{}: skipping processing of OFAction", e);
            return;
        }

        if (match.isFlowMod()) {
            final OVXFlowMod fm;
            try {
                fm = sw.getFlowMod(match.getCookie());
            } catch (MappingException e) {
                log.warn("FlowMod not found in our FlowTable");
                return;
            }

            fm.setOFMessage(fm.getFlowMod().createBuilder()
                    .setCookie(U64.of(match.getCookie()))
                    .build()
            );

            for (final OVXPort outPort : outPortList) {
                Integer linkId = 0;
                Integer flowId = 0;

                if (sw instanceof OVXBigSwitch
                        && inPort.getPhysicalPort().getParentSwitch()
                        != outPort.getPhysicalPort().getParentSwitch()) {
                }else {
                    /*
                     * SingleSwitch and BigSwitch with inPort & outPort
                     * belonging to the same physical switch
                     */
                    if (inPort.isEdge()) {
                        if (outPort.isEdge()) {
                            
                        	flowId = getFlowId(vnet, sw, match);
                            match.setMplsInfo(SwitchType.SAME, flowId, vnet.getTenantId());      
                        } else {
                            
                            flowId = getFlowId(vnet, sw, match);
                            match.setMplsInfo(SwitchType.INGRESS, flowId, vnet.getTenantId());

                        }
                    } else {
                        if (outPort.isEdge()) {
                            
                            final OVXPort dstPort = vnet
                                    .getNeighborPort(inPort);
                            final OVXLink link = dstPort.getLink().getOutLink();
                            if (link != null) {

                            	flowId = getFlowId(vnet, sw, match);
                                match.setMplsInfo(SwitchType.EGRESS, flowId, vnet.getTenantId());
                                    
                            } else {
                                // TODO: substitute all the return with
                                // exceptions
                                this.log.error(
                                        "Cannot retrieve the virtual link between ports {} {}, dropping message",
                                        dstPort, inPort);
                                return;
                            }
                        } else {
                            //System.out.printf("4 !inPort.isEdge() && !outPort.isEdge()\n");

                            final OVXLink link = outPort.getLink().getOutLink();
                            linkId = link.getLinkId();
                            flowId = getFlowId(vnet, sw, match);
                            match.setMplsInfo(SwitchType.INTERMEDIATE, flowId, vnet.getTenantId());
                              
                        }
                    }
                    if (inPort.getPhysicalPortNumber() != outPort.getPhysicalPortNumber()) {
                        approvedActions.add(
                                ofFactory.actions().buildOutput()
                                        .setPort(OFPort.of(outPort.getPhysicalPortNumber()))
                                        .build()
                        );
                    } else {
                        approvedActions.add(
                                ofFactory.actions().buildOutput()
                                        .setPort(OFPort.IN_PORT)
                                        .build()
                        );
                    }
                }
            }
        }else if (match.isPacketOut()) {
            boolean throwException = true;

            for (final OVXPort outPort : outPortList) {

                if (outPort.isLink()) {
                    final OVXPort dstPort = outPort.getLink().getOutLink()
                            .getDstPort();

                    dstPort.getParentSwitch().sendMsg(
                            new OVXPacketIn(match.getPktData(),
                                    dstPort.getPortNumber(), sw.getOfVersion()), sw);

                    this.log.debug(
                            "Generate a packetIn from OVX Port {}/{}, physicalPort {}/{}",
                            dstPort.getParentSwitch().getSwitchName(),
                            dstPort.getPortNumber(), dstPort.getPhysicalPort()
                                    .getParentSwitch().getSwitchName(),
                            dstPort.getPhysicalPortNumber());
                }else if (sw instanceof OVXBigSwitch) {
                    /**
                     * Big-switch management. Generate a packetOut to the
                     * physical outPort
                     */
                    // Only generate pkt_out if a route is configured between in
                    // and output port.
                    // If parent switches are identical, no route will be configured
                    // although we do want to output the pkt_out.
                    if ((inPort == null)
                            || (inPort.getParentSwitch() == outPort.getParentSwitch())
                            || (((OVXBigSwitch) sw).getRoute(inPort, outPort) != null)) {
                        final PhysicalPort dstPort = outPort.getPhysicalPort();
                        dstPort.getParentSwitch().sendMsg(
                                new OVXPacketOut(match.getPktData(),
                                        OFPort.ANY.getShortPortNumber(),
                                        dstPort.getPortNumber(), sw.getOfVersion()), sw);
                        this.log.debug("PacketOut for a bigSwitch port, "
                                        + "generate a packet from Physical Port {}/{}",
                                dstPort.getParentSwitch().getSwitchName(),
                                dstPort.getPortNumber());
                    }
                } else {
                    /**
                     * Else (e.g. the outPort is an edgePort in a single switch)
                     * modify the packet and send to the physical switch.
                     */
                    throwException = false;
                    //log.info("prependUnRewriteActions3");
                    /*approvedActions.addAll(
                            IPMapper.prependUnRewriteActions(match.getMatch())
                    );*/

                    OFAction tempAction = ofFactory.actions().buildOutput()
                            .setPort(OFPort.of(outPort.getPhysicalPortNumber()))
                            .build();

                    approvedActions.add(tempAction);

                    this.log.debug(
                            "Physical ports are on the same physical switch, rewrite only outPort to {}",
                            outPort.getPhysicalPortNumber());
                }

            }

            if (throwException) {
                throw new DroppedMessageException();
            }
        }
    }

    private LinkedList<OVXPort> fillPortList(final Short inPort,
                                             final Short outPort, final OVXSwitch sw)
            throws DroppedMessageException {

        final LinkedList<OVXPort> outPortList = new LinkedList<OVXPort>();
        if (U16.f(outPort) < U16.f(OFPort.MAX.getShortPortNumber())) {
            if (sw.getPort(outPort) != null && sw.getPort(outPort).isActive()) {
                outPortList.add(sw.getPort(outPort));
            }
        } else if (U16.f(outPort) == U16.f(OFPort.FLOOD.getShortPortNumber())) {
            final Map<Short, OVXPort> ports = sw.getPorts();
            for (final OVXPort port : ports.values()) {
                if (port.getPortNumber() != inPort && port.isActive()) {
                    outPortList.add(port);
                }
            }
        } else if (U16.f(outPort) == U16.f(OFPort.ALL.getShortPortNumber())) {
            final Map<Short, OVXPort> ports = sw.getPorts();
            for (final OVXPort port : ports.values()) {
                if (port.isActive()) {
                    outPortList.add(port);
                }
            }
        } else {
            /*log.info(
                    "Output port from controller currently not supported. Short = {}, Exadecimal = 0x{}, {}",
                    U16.f(outPort),
                    Integer.toHexString(U16.f(outPort) & 0xffff),
                    OFPort.CONTROLLER);*/
        }

        if (outPortList.size() < 1) {
            throw new DroppedMessageException(
                    "No output ports defined; dropping");
        }
        return outPortList;
    }

    @Override
    public int hashCode() {
        return this.getAction().hashCode();
    }
    
    private int getFlowId(final OVXNetwork vnet,OVXSwitch sw,OVXMatch match){
    	int flowId = 0;
		try {
			flowId = vnet.getFlowManager().getL3FlowId(
			        match.getMatch().get(MatchField.ETH_SRC).getBytes(),
			        match.getMatch().get(MatchField.ETH_DST).getBytes(),
			        match.getMatch().get(MatchField.IPV4_SRC).getBytes(),
			        match.getMatch().get(MatchField.IPV4_DST).getBytes()
			        //match.getMatch().get(MatchField.TCP_SRC).getPort(),
			        //match.getMatch().get(MatchField.TCP_DST).getPort()
			        );
			log.debug("tenant {} : flowid = {}", vnet.getTenantId(), flowId);
		} catch (UnsupportedOperationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (DroppedMessageException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IndexOutOfBoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
		return (vnet.getTenantId() << 20) + (flowId); 
		
    	//return flowId;
    }
}
