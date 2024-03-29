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

import net.onrc.openvirtex.elements.datapath.OVXSwitch;
import net.onrc.openvirtex.elements.datapath.PhysicalSwitch;
import net.onrc.openvirtex.exceptions.MappingException;
import net.onrc.openvirtex.services.forwarding.mpls.MplsForwarding;
import net.onrc.openvirtex.services.path.physicalpath.PhysicalPathBuilder;
import net.onrc.openvirtex.services.path.virtualpath.VirtualPath;
import net.onrc.openvirtex.services.path.virtualpath.VirtualPathBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.OFFlowRemoved;
import org.projectfloodlight.openflow.protocol.OFMessage;

public class OVXFlowRemoved extends OVXMessage implements Virtualizable {

    Logger log = LogManager.getLogger(OVXFlowRemoved.class.getName());

    public OVXFlowRemoved(OFMessage msg) {

        super(msg);
    }

    public OFFlowRemoved getFlowRemoved() {
        return (OFFlowRemoved)this.getOFMessage();
    }

    @Override
    public void virtualize(final PhysicalSwitch sw) {
        this.log.debug("virtualize");

        this.log.debug(this.getFlowRemoved().toString());


        long thisCookie = this.getFlowRemoved().getCookie().getValue();
        int tid = (int) ( thisCookie >> 32);


        //for VM
        Integer pathID = MplsForwarding.getInstance().getPathIDFromMatch(this.getFlowRemoved().getMatch(), sw, tid);
        //Integer flowId = null;// = PhysicalPathBuilder.getInstance().getFlowID(this.getFlowRemoved().getCookie());

        //log.info("Cookie {}", getFlowRemoved().getCookie().toString());

        if(pathID != null) {
            log.debug("PathID != null pathID [{}]", pathID);
            VirtualPath vPath = VirtualPathBuilder.getInstance().getVirtualPath(pathID);
            if (vPath != null) {
                log.debug("vPath != null");
                if (vPath.isMigrated()) {
                    log.debug("PathID [{}] is migrated", pathID);
                    return;
                }else{
                    log.debug("PathID [{}] is not migrated", pathID);
                }
            }else{
                log.debug("vPath == null");
                return;
            }
        }else{
            log.debug("PathID == null " + this.getFlowRemoved().toString());
        }

        /* a PhysSwitch can be a OVXLink */
        if (!(sw.getMap().hasVirtualSwitch(sw, tid))) {
            return;
        }
        try {
            OVXSwitch vsw = sw.getMap().getVirtualSwitch(sw, tid);
            /*
             * If we are a Big Switch we might receive multiple same-cookie FR's
             * from multiple PhysicalSwitches. Only handle if the FR's newly
             * seen
             */
            if (vsw.getFlowTable().hasFlowMod(thisCookie)) {
                OVXFlowMod fm = vsw.getFlowMod(thisCookie);
                /*
                 * send north ONLY if tenant controller wanted a FlowRemoved for
                 * the FlowMod
                 */
                vsw.deleteFlowMod(thisCookie);
                if (fm.getFlowMod().getFlags().contains(OFFlowModFlags.SEND_FLOW_REM)) {

                    this.setOFMessage(
                            this.getFlowRemoved().createBuilder()
                                    .setCookie(fm.getFlowMod().getCookie())
                                    .setMatch(fm.getFlowMod().getMatch())
                                    .setPriority(fm.getFlowMod().getPriority())
                                    .setIdleTimeout(fm.getFlowMod().getIdleTimeout())
                                    .build()
                    );
                    log.info("send to flowremoved to controller {}", this.getFlowRemoved().toString());
                    vsw.sendMsg(this, sw);
                }

            }
        } catch (MappingException e) {
            log.warn("Exception fetching FlowMod from FlowTable: {}", e);
        }
    }
    @Override
    public String toString() {
        return "OVXFlowRemoved: cookie = " + this.getFlowRemoved().getCookie().getValue()
                + " priority = " + this.getFlowRemoved().getPriority()
                + " match = " + this.getFlowRemoved().getMatch().toString()
                + " reason = " + this.getFlowRemoved().getReason();
    }

    @Override
    public int hashCode() {
        return this.getOFMessage().hashCode();
    }
}
