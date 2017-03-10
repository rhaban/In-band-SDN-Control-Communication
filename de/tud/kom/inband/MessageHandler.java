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

package de.tud.kom.inband;

import java.util.Set;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import de.tud.kom.inband.eval.EvalCollector;
import de.tud.kom.inband.service.ControllerCommunicationService.MessageListener;
import de.tud.kom.inband.util.ControlMessageParser;
import de.tud.kom.inband.util.ControllerConnection;
import de.tud.kom.inband.util.OFMessageBuilder;
import de.tud.kom.inband.util.Port;
import de.tud.kom.inband.util.Routing;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;

public class MessageHandler {

	private int controllerId;
	private ConnectionManager connectionManager;
	private IOFSwitchService switchService;

	public MessageHandler(int controllerId, ConnectionManager connectionManager, IOFSwitchService switchService) {
		this.controllerId = controllerId;
		this.connectionManager = connectionManager;
		this.switchService = switchService;
	}

	/**
	 * process a received discovery message: - extract information (source,
	 * costs, ..) - store connection if from other controller
	 * 
	 * @param inSwitch
	 *            switch where the discovery was received
	 * @param msg
	 *            discovery of packet-in message
	 * @param eth
	 *            discovery packet
	 */
	public void processDiscovery(IOFSwitch inSwitch, OFMessage msg, Ethernet eth) {
		OFPort inPort = ((OFPacketIn) msg).getMatch().get(MatchField.IN_PORT);
		IPv4 ip = (IPv4) eth.getPayload();
		UDP udp = (UDP) ip.getPayload();
		int sourceController = udp.getSourcePort().getPort();
		int targetController = udp.getDestinationPort().getPort();
		
		// use only for internal routing if its from itself
		if (sourceController == this.controllerId)
		{
			System.out.println("Intra network connection from " + sourceController + " to " + this.controllerId + " (this)");
			EvalCollector.get().incrementIntraNetworkDiscoveries();
			Routing.addIntraNetworkLink(inSwitch, inPort, switchService.getSwitch(DatapathId.of(ip.getSourceAddress().getInt())));
//			Set<Integer> connectableControllers = Routing.intermediateRouteUsingPort(connectionManager, switchService, inSwitch, inPort);
//			if(connectableControllers.size() > 0) 
//			{
//				System.out.println("Connectable controllers found:" + connectableControllers);
//			}
//			else
//			{
//				System.out.println("No further controllers connectable");
//			}
			//TODO check if two others could be connected using this link (better costs?)
			return;
		}
		// forward to corresponding controller if target controller given and
		// its not itself
		if (targetController != 1024 && targetController != this.controllerId)
		{
			return;
		}

		Data data = (Data) udp.getPayload();
		String info = new String(data.getData());
		int costs = Integer.parseInt(ControlMessageParser.getValue(info, "costs"));

		// store connection
		this.connectionManager.storeAndUseConnection(sourceController, inSwitch, inPort, costs, true);
	}

	/**
	 * process activation message
	 * 
	 * Once a discovery has been answered with a activation message this path is
	 * used by the discovered partner: (1) install rule an ingress switch
	 * 
	 * @param inSwitch
	 *            where the activation message was received at
	 * @param msg
	 *            activation of packet-in message
	 * @param eth
	 *            activation message
	 */
	public void processActivation(IOFSwitch inSwitch, OFMessage msg, Ethernet eth) {
		OFPort inPort = ((OFPacketIn) msg).getMatch().get(MatchField.IN_PORT);
		IPv4 ip = (IPv4) eth.getPayload();
		UDP udp = (UDP) ip.getPayload();
		int sourceController = udp.getSourcePort().getPort();
		int targetController = udp.getDestinationPort().getPort();
		String data = new String(((Data) udp.getPayload()).getData());
		int costs = Integer.parseInt(ControlMessageParser.getValue(data, "costs"));

		// install rule at ingress switch if destination is current controller
		if (targetController == this.controllerId)
		{
			System.out.println("Activate path from " + sourceController + " to this at " + inSwitch.getId().getLong() + ":" + inPort);
			this.connectionManager.installLastHopCCRulePath(inSwitch, inPort);

			// use connection!
			if (!connectionManager.hasConnectionAt(sourceController, inSwitch, inPort))
			{
				connectionManager.storeAndUseConnection(sourceController, inSwitch, inPort, costs, false);
			}
		}
		// else forward activation and install path
		else
		{
			ControllerConnection connectionTo = this.connectionManager.getConnectionTo(targetController);
			Port edge = Routing.installRulesBetween(sourceController, inSwitch, inPort, targetController, connectionTo.getSwitch(), connectionTo.getPort());
			if (edge != null)
			{
				byte[] activatePacket = OFMessageBuilder.activatePacket(sourceController, targetController, costs);
				OFPacketOut pckOut = OFMessageBuilder.packetOut(edge.getSwitch_(), edge.getPort(), activatePacket);
				edge.getSwitch_().write(pckOut);
				EvalCollector.get().incrementActivationMessageCounter();
				System.out.println("Activate path from " + sourceController + " to " + targetController + " at " + inSwitch.getId().getLong() + ":"
						+ inPort + " --> " + edge.getSwitch_().getId().getLong() + ":" + edge.getPort().getPortNumber() + " and install path.");
			}
		}
	}

	public void processCommunicationMessage(Ethernet eth, Set<MessageListener> listeners) {
		IPv4 ip = (IPv4) eth.getPayload();
		UDP udp = (UDP) ip.getPayload();
		int foreignController = udp.getSourcePort().getPort();

		Data data = (Data) udp.getPayload();
		String msg = new String(data.getData());
		for (MessageListener l : listeners)
		{
			l.onMessage(foreignController, msg);
		}
	}

}
