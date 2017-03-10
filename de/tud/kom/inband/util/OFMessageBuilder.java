/**
    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.
    
    
    @author Rhaban Hark
**/

package de.tud.kom.inband.util;

import java.util.Collections;
import java.util.List;

import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.TransportPort;

import com.google.common.collect.ImmutableSet;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;

public class OFMessageBuilder {

	public static final OFVlanVidMatch OF_VLAN_DISCOVERY = OFVlanVidMatch.ofVlan(1001);
	public static final OFVlanVidMatch OF_VLAN_COMMUNICATION = OFVlanVidMatch.ofVlan(1002);
	private static final int FLOW_PRIORITY_DEFAULT = 0xfff;
	private static final int FLOW_PRIORITY_HIGH = 0xfff1;
	private static final ImmutableSet<OFFlowModFlags> FLOW_FLAGS_DEFAULT = ImmutableSet.<OFFlowModFlags>of(OFFlowModFlags.SEND_FLOW_REM,
			OFFlowModFlags.CHECK_OVERLAP);
	public static final MacAddress DISCOVERY_MAC = MacAddress.of("00:00:00:00:01:f0");
	public static final MacAddress ACTIVATE_MAC = MacAddress.of("00:00:00:00:01:f1");
	public static final int IDLE_TIMEOUT = 5;

	public static OFFlowAdd flowModDiscovery(IOFSwitch activeSwitch) {
		OFFactory factory = activeSwitch.getOFFactory();

		List<OFAction> actions = Collections.singletonList(factory.actions().buildOutput().setPort(OFPort.CONTROLLER).setMaxLen(0xffFFffFF).build());
		List<OFInstruction> instr = Collections.singletonList(factory.instructions().buildApplyActions().setActions(actions).build());
		Match match = factory.buildMatch().setExact(MatchField.VLAN_VID, OF_VLAN_DISCOVERY).build();
		OFFlowAdd flowAdd = factory.buildFlowAdd().setActions(actions).setInstructions(instr).setMatch(match).setIdleTimeout(0).setHardTimeout(0)
				.setPriority(FLOW_PRIORITY_DEFAULT).setFlags(FLOW_FLAGS_DEFAULT).build();
		return flowAdd;
	}

	public static OFFlowAdd flowModIntermediateControllerPath(int sourceController, OFPort inPort, int targetController, IOFSwitch outSwitch,
			OFPort outPort) {
		OFFactory factory = outSwitch.getOFFactory();

		List<OFAction> actions = Collections.singletonList(factory.actions().buildOutput().setPort(outPort).build());
		List<OFInstruction> instr = Collections.singletonList(factory.instructions().buildApplyActions().setActions(actions).build());
		Match match = factory.buildMatch().setExact(MatchField.VLAN_VID, OF_VLAN_COMMUNICATION).setExact(MatchField.IN_PORT, inPort)
				.setExact(MatchField.ETH_TYPE, EthType.IPv4).setExact(MatchField.IP_PROTO, IpProtocol.UDP)
				.setExact(MatchField.UDP_SRC, TransportPort.of(sourceController)).setExact(MatchField.UDP_DST, TransportPort.of(targetController))
				.build();
		OFFlowAdd flowAdd = factory.buildFlowAdd().setActions(actions).setInstructions(instr).setMatch(match).setIdleTimeout(IDLE_TIMEOUT).setHardTimeout(0)
				.setPriority(FLOW_PRIORITY_HIGH).setFlags(FLOW_FLAGS_DEFAULT).build();
		return flowAdd;
	}

	public static OFFlowAdd flowModControllerPath(IOFSwitch sw, OFPort port, int targetController) {
		OFFactory factory = sw.getOFFactory();

		List<OFAction> actions = Collections.singletonList(factory.actions().buildOutput().setPort(OFPort.CONTROLLER).setMaxLen(0xffFFffFF).build());
		List<OFInstruction> instr = Collections.singletonList(factory.instructions().buildApplyActions().setActions(actions).build());
		Match match = factory.buildMatch().setExact(MatchField.VLAN_VID, OF_VLAN_COMMUNICATION).setExact(MatchField.IN_PORT, port)
				.setExact(MatchField.ETH_TYPE, EthType.IPv4).setExact(MatchField.IP_PROTO, IpProtocol.UDP)
				.setExact(MatchField.UDP_DST, TransportPort.of(targetController)).build();
		OFFlowAdd flowAdd = factory.buildFlowAdd().setActions(actions).setInstructions(instr).setMatch(match).setIdleTimeout(IDLE_TIMEOUT).setHardTimeout(0)
				.setPriority(FLOW_PRIORITY_HIGH).setFlags(FLOW_FLAGS_DEFAULT).build();
		return flowAdd;
	}

	public static byte[] activatePacket(int sourcecontroller, int targetController, int costs) {
		Ethernet l2 = new Ethernet();
		l2.setSourceMACAddress(ACTIVATE_MAC);
		l2.setDestinationMACAddress(MacAddress.BROADCAST);
		l2.setVlanID(OF_VLAN_DISCOVERY.getVlan());
		l2.setEtherType(EthType.IPv4);
		IPv4 l3 = new IPv4();
		l3.setSourceAddress(IPv4Address.of("10.0.0.1"));
		l3.setDestinationAddress(IPv4Address.of("10.0.0.255"));
		l3.setTtl((byte) 64);
		l3.setProtocol(IpProtocol.UDP);
		UDP l4 = new UDP();
		l4.setSourcePort(TransportPort.of(sourcecontroller));
		l4.setDestinationPort(TransportPort.of(targetController));
		Data l7 = new Data();
		l7.setData(("signature=DUMMY,costs=" + costs).getBytes());

		l2.setPayload(l3);
		l3.setPayload(l4);
		l4.setPayload(l7);

		byte[] serializedData = l2.serialize();
		return serializedData;
	}

	public static byte[] discoveryPacket(int costs, int controllerId, int targetController, int switchId) {
		// https://floodlight.atlassian.net/wiki/display/floodlightcontroller/How+to+Create+a+Packet+Out+Message
		Ethernet l2 = new Ethernet();
		l2.setSourceMACAddress(DISCOVERY_MAC);
		l2.setDestinationMACAddress(MacAddress.BROADCAST);
		l2.setVlanID(OF_VLAN_DISCOVERY.getVlan());
		l2.setEtherType(EthType.IPv4);
		IPv4 l3 = new IPv4();
		l3.setSourceAddress(IPv4Address.of(switchId));
		l3.setDestinationAddress(IPv4Address.of("10.0.0.255"));
		l3.setTtl((byte) 64);
		l3.setProtocol(IpProtocol.UDP);
		UDP l4 = new UDP();
		l4.setSourcePort(TransportPort.of(controllerId));
		l4.setDestinationPort(TransportPort.of(targetController));
		Data l7 = new Data();
		l7.setData(("costs=" + costs + ",signature=DUMMY").getBytes());

		l2.setPayload(l3);
		l3.setPayload(l4);
		l4.setPayload(l7);

		byte[] serializedData = l2.serialize();
		return serializedData;
	}

	public static byte[] controllerMessage(int controllerId, int targetControllerId, String message) {
		Ethernet l2 = new Ethernet();
		l2.setSourceMACAddress(MacAddress.of("0" + controllerId + ":00:00:00:01:f2"));
		l2.setDestinationMACAddress(MacAddress.of("0" + targetControllerId + ":00:00:00:01:f2"));
		l2.setVlanID(OF_VLAN_COMMUNICATION.getVlan());
		l2.setEtherType(EthType.IPv4);
		IPv4 l3 = new IPv4();
		l3.setSourceAddress(IPv4Address.of("10.0.0." + controllerId));
		l3.setDestinationAddress(IPv4Address.of("10.0.0." + targetControllerId));
		l3.setTtl((byte) 64);
		l3.setProtocol(IpProtocol.UDP);
		UDP l4 = new UDP();
		l4.setSourcePort(TransportPort.of(controllerId));
		l4.setDestinationPort(TransportPort.of(targetControllerId));
		Data l7 = new Data();
		l7.setData(message.getBytes());

		l2.setPayload(l3);
		l3.setPayload(l4);
		l4.setPayload(l7);

		byte[] serializedData = l2.serialize();
		return serializedData;
	}

	public static OFPacketOut packetOut(IOFSwitch switch_, OFPort port, byte[] serializedData) {
		OFPacketOut po = switch_.getOFFactory().buildPacketOut().setData(serializedData)
				.setActions(Collections.singletonList((OFAction) switch_.getOFFactory().actions().output(port, 0xffFFffFF)))
				.setInPort(OFPort.CONTROLLER).build();
		return po;
	}

}
