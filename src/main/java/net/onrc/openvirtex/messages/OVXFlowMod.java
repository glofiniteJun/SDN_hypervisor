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
package net.onrc.openvirtex.messages;

import java.util.*;

import net.onrc.openvirtex.elements.datapath.FlowTable;
import net.onrc.openvirtex.elements.datapath.OVXFlowTable;
import net.onrc.openvirtex.elements.datapath.OVXSwitch;
import net.onrc.openvirtex.elements.port.OVXPort;
import net.onrc.openvirtex.exceptions.*;
import net.onrc.openvirtex.messages.actions.*;
import net.onrc.openvirtex.protocol.OVXMatch;
import net.onrc.openvirtex.services.path.physicalflow.PhysicalFlow;
import net.onrc.openvirtex.services.path.physicalpath.PhysicalPath;
import net.onrc.openvirtex.services.path.physicalpath.PhysicalPathBuilder;
import net.onrc.openvirtex.services.path.virtualpath.VirtualPath;
import net.onrc.openvirtex.services.path.virtualpath.VirtualPathBuilder;
import net.onrc.openvirtex.util.OVXUtil;
import net.oslab.libera.p4.basic.P4Handler;

import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.protocol.action.OFAction;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.*;


public class OVXFlowMod extends OVXMessage implements Devirtualizable {

    private final Logger log = LogManager.getLogger(OVXFlowMod.class.getName());

    private OVXSwitch sw = null;
    private final List<OFAction> approvedActions = new LinkedList<OFAction>();
    private OFFlowMod originalFlowMod = null;

    private long ovxCookie = -1;

    public OVXFlowMod(OFMessage msg) {
        super(msg);
    }

    public OFFlowMod getFlowMod() {
        return (OFFlowMod)this.getOFMessage();
    }

    public VirtualPath virtualPath = null;

    @Override
    public synchronized void devirtualize(final OVXSwitch sw) {

        //this.log.info("Before " + this.getOFMessage().toString());
        List<OFAction> laction = this.getFlowMod().getActions();
        if(laction != null) {
            if(laction.size() == 1) {
                OFAction ofAction = laction.get(0);
                if(ofAction.getType() == OFActionType.OUTPUT) {
                    OFActionOutput ofActionOutput = (OFActionOutput) ofAction;
                    if(ofActionOutput.getPort() != OFPort.CONTROLLER) {


                    }else {
                        return;
                    }
                }
            }
        }

         // Drop LLDP-matching messages sent by some applications
        if (this.getFlowMod().getMatch().get(MatchField.ETH_TYPE) == EthType.LLDP) {
            return;
        }




        this.originalFlowMod = this.getFlowMod().createBuilder().build();

        this.sw = sw;
        FlowTable ft = this.sw.getFlowTable();

        int bufferId = OFBufferId.NO_BUFFER.getInt();
        if (sw.getFromBufferMap(this.getFlowMod().getBufferId().getInt()) != null) {
            bufferId = ((OFPacketIn)sw.getFromBufferMap(this.getFlowMod().getBufferId().getInt()).getOFMessage())
                    .getBufferId().getInt();
        }


        //OFMatch에서 inport의 기본값은 0으로 설정되기 때문, 그러나 OpenFlowj에서는 MatchField가 존재하지 않으면
        //필드 자체가 없기 때문에 inport값을 알 수 없다.
        //ONOS인 경우 스위치가 연결되면 기본적인 설정 FlowMod 메시지를 보낸다(ARP, LLDP, IPv4정보를 Controller로 보내는 설정)
        //거기엔 in_port 정보가 없다 추후 이부분의 루틴 구현해야함

        short inport = 0;

        if(this.getFlowMod().getMatch().get(MatchField.IN_PORT) != null)
        {
           inport = this.getFlowMod().getMatch()
                   .get(MatchField.IN_PORT).getShortPortNumber();
        }
        boolean pflag = ft.handleFlowMods(this.clone());

        OVXMatch ovxMatch = new OVXMatch(this.getFlowMod().getMatch());

        ovxCookie = ((OVXFlowTable) ft).getCookie(this, false);

        //log.info("FlowMod Original Cookie{} {}", this.getFlowMod().getCookie().toString(), this.getFlowMod().hashCode());

        ovxMatch.setOVXSwitch(sw);
        ovxMatch.setCookie(ovxCookie);

        this.setOFMessage(this.getFlowMod().createBuilder()
                .setCookie(U64.of(ovxMatch.getCookie()))
                .build()
        );



        for (final OFAction act : this.getFlowMod().getActions()) {
            try {
                OVXAction action2 = OVXActionUtil.wrappingOVXAction(act);

                ((VirtualizableAction) action2).virtualize(sw, this.approvedActions, ovxMatch);
            } catch (final ActionVirtualizationDenied e) {
                this.log.info("Action {} could not be virtualized; error: {}",
                        act, e.getMessage());
                ft.deleteFlowMod(ovxCookie);
                sw.sendMsg(OVXMessageUtil.makeError(e.getErrorCode(), this), sw);
                return;
            } catch (final DroppedMessageException e) {
                //this.log.info("Dropping ovxFlowMod {} {}", this.getOFMessage().toString(), e);
                ft.deleteFlowMod(ovxCookie);
                // TODO perhaps send error message to controller
                return;
            } catch (final NullPointerException e) {
                this.log.info("Action {} could not be supported", act);
                return;
            }
        }

        //added for MPLS
        switch(this.getFlowMod().getCommand()) {
            case ADD:
                if(ovxMatch.getFlowId() != null ) {
                    this.virtualPath = VirtualPathBuilder.getInstance().buildVirtualPath(ovxMatch, originalFlowMod);

                    if(this.virtualPath == null)
                        return;
                }else{
                    return;
                }
                break;
            case MODIFY:
            case MODIFY_STRICT:
                break;
            case DELETE:
            case DELETE_STRICT:
                this.originalFlowMod = VirtualPathBuilder.getInstance().removeVirtualPath(this.originalFlowMod, ovxMatch);
                if(this.originalFlowMod == null) {
                    log.info("Matching FlowMod message does not exist");
                    return;
                }
                //Match temp = MplsManager.getInstance().DeleteMplsActions(ovxMatch);

                break;
        }

        final OVXPort ovxInPort = sw.getPort(inport);
        this.setOFMessage(this.getFlowMod().createBuilder()
                .setBufferId(OFBufferId.of(bufferId))
                .build()
        );

        if (ovxInPort == null) {
            if(this.getFlowMod().getMatch().isFullyWildcarded(MatchField.IN_PORT)) {

                for (OVXPort iport : sw.getPorts().values()) {
                    try {
                        prepAndSendSouth(iport, pflag, ovxMatch);
                    } catch (IndexOutOfBoundException e) {
                        e.printStackTrace();
                    }catch (LinkMappingException e) {
        				// TODO Auto-generated catch block
        				e.printStackTrace();
        			}
                }
            } else {
                this.log.error(
                        "Unknown virtual port id {}; dropping ovxFlowMod {}",
                        inport, this);
                sw.sendMsg(OVXMessageUtil.makeErrorMsg(OFFlowModFailedCode.EPERM, this), sw);
                return;
            }
        } else {

            try {
				prepAndSendSouth(ovxInPort, pflag, ovxMatch);
            } catch (IndexOutOfBoundException e) {
                e.printStackTrace();
            }catch (LinkMappingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
    }

    public void modifyMatch(Match match)
    {
        this.setOFMessage(this.getFlowMod().createBuilder()
                .setMatch(match)
                .build()
        );
    }

    private void prepAndSendSouth(OVXPort inPort, boolean pflag, OVXMatch ovxMatch) throws IndexOutOfBoundException, LinkMappingException{
        if (!inPort.isActive()) {
            log.warn("Virtual network {}: port {} on switch {} is down.",
                    sw.getTenantId(), inPort.getPortNumber(),
                    sw.getSwitchName());
            return;
        }

        this.modifyMatch(
                OVXMessageUtil.updateMatch(
                        this.getFlowMod().getMatch(),
                        this.getFlowMod().getMatch().createBuilder()
                                .setExact(MatchField.IN_PORT, OFPort.of(inPort.getPhysicalPortNumber()))
                                .build()
                )
        );

        OVXMessageUtil.translateXid(this, inPort);
        

        this.setOFMessage(this.getFlowMod().createBuilder()
                .setActions(this.approvedActions)
                //.setHardTimeout(5000)//ksyang
                .build()
        );

        if (pflag) {

            if(!this.getFlowMod().getFlags().contains(OFFlowModFlags.SEND_FLOW_REM))
                this.getFlowMod().getFlags().add(OFFlowModFlags.SEND_FLOW_REM);



            switch(this.getFlowMod().getCommand()) {
                case ADD:
                    if(ovxMatch.getFlowId() != null) {

					PhysicalFlow pFlow = PhysicalPathBuilder.getInstance().buildPhysicalPath(
                                this.virtualPath, this.originalFlowMod, this.getFlowMod(), ovxMatch.getSwitchType(),
                                inPort.getPhysicalPort().getParentSwitch());
					
					PhysicalPath mainPath = pFlow.getMainPath();
					
                        if(this.virtualPath.isBuild()) {
                        	this.log.info("{} - virtual isBuild: true", pFlow.getFlowId());
                        	// install main path
                        	if(pFlow.isInstalled()){
                        		this.log.info("Physical flow{} is allreay installed", pFlow.getFlowId());
                        	}
                        	else{
                        		mainPath.sendSouth();
                        	}
                        	
                        	if(mainPath.isBuild()){
                        		this.log.info("{} - physical isBuild: true", pFlow.getFlowId());
                        		/* core function of TALON */
                        		pFlow.assignPaths();
                        		
                        		
                        		/* Transform to P4 rule */
                        		//if(pFlow.buildIsCompleted()){
                        		//	P4Handler.getInstance().createFlowRule(pFlow);
                        		//}
                        	}
                        	else{
                        		this.log.info("{} - physical isBuild: false", pFlow.getFlowId());
                        	}
                        }
                        else{
                        	this.log.info("{} - virtual isBuild: false", pFlow.getFlowId());
                        }

                    }else{
                        return;
                    }
                    break;
                case DELETE:
                case DELETE_STRICT:

                    if(ovxMatch.getFlowId() != null) {
                        //this.log.info("------------- {}", this.getOFMessage().toString());


                        OFFlowMod ofFlowMod = PhysicalPathBuilder.getInstance().removePhysicalPath(this.originalFlowMod, ovxMatch);
                        if (ofFlowMod != null) {
                            //this.log.info("ofFlowMod != null");
                            this.modifyMatch(ofFlowMod.getMatch());
                        } else {
                            //this.log.info("ofFlowMod == null");
                        }

                        sw.sendSouth(this, inPort);

                        //this.log.info("+++++++++++++ {}", this.getOFMessage().toString());
                    }else{
                        return;
                    }

                    break;
            }

        }
    }


    public OVXFlowMod clone() {
        OVXFlowMod flowMod = new OVXFlowMod(this.getOFMessage().createBuilder().build());
        return flowMod;
    }

    public Map<String, Object> toMap() {
        final Map<String, Object> map = new LinkedHashMap<String, Object>();
        if (this.getFlowMod().getMatch() != null) {
            map.put("match", new OVXMatch(this.getFlowMod().getMatch()).toMap());
        }
        LinkedList<Map<String, Object>> actions = new LinkedList<Map<String, Object>>();
        for (OFAction act : this.getFlowMod().getActions()) {
            try {
                actions.add(OVXUtil.actionToMap(act));
            } catch (UnknownActionException e) {
                log.warn("Ignoring action {} because {}", act, e.getMessage());
            }
        }
        map.put("actionsList", actions);
        map.put("priority", String.valueOf(this.getFlowMod().getPriority()));
        return map;
    }

    @Override
    public int hashCode() {
        return this.getOFMessage().hashCode();
    }
}
