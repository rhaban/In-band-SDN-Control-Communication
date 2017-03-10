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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.types.OFPort;

import de.tud.kom.inband.eval.EvalCollector;
import de.tud.kom.inband.util.ControllerConnection;
import de.tud.kom.inband.util.OFMessageBuilder;
import de.tud.kom.inband.util.Routing;
import net.floodlightcontroller.core.IOFSwitch;

public class ConnectionManager {

	private Map<Integer, List<ControllerConnection>> connections;
	private int controllerId;

	public ConnectionManager(int controllerId) {
		this.connections = new HashMap<>();
		this.controllerId = controllerId;
	}

	/**
	 * Send discovery message to the specified port (which has just been
	 * detected)
	 * 
	 * @param switch
	 *            the connected
	 * @param port
	 *            which just got active
	 * @param targetController
	 *            if a target controlles is given the discovery will be
	 *            forwarded to the target controller or droppped
	 */
	public void discoverPort(IOFSwitch switch_, OFPort port) {
		byte[] serializedData = OFMessageBuilder.discoveryPacket(1, this.controllerId, 1024, (int) switch_.getId().getLong());
		OFPacketOut po = OFMessageBuilder.packetOut(switch_, port, serializedData);
		EvalCollector.get().incrementDisoveryMessageCounter();
		switch_.write(po);
	}

	/**
	 * Store the discovered connection to the controller in a list for the
	 * source controller ordered by their costs
	 * 
	 * If the found connection is the best which was found so far send a
	 * connection activation
	 * 
	 * @param sourceController
	 * @param sw
	 *            where the discovery was received
	 * @param inPort
	 *            where the discovery was received
	 * @param costs
	 *            for this connection
	 * @param relayed
	 * @return the position in the connections list, 0 indicates the first
	 *         (best) connection
	 */
	public int storeAndUseConnection(int sourceController, IOFSwitch sw, OFPort inPort, int costs, boolean broadcast) {
		int index = storeConnection(sourceController, sw, inPort, costs);
		if (index == 0)
		{
			/*
			 * if connection is the best so far, activate it
			 */
			activateConnection(sourceController, sw, inPort, costs);
			if (broadcast)
			{
				broadcastDiscovery(sourceController, sw, inPort, costs);
			}
		}
		return index;
	}

	public int storeConnection(int sourceController, IOFSwitch inSwitch, OFPort inPort, int costs) {
		ControllerConnection connection = new ControllerConnection(inSwitch, inPort, costs);
		List<ControllerConnection> connections = lazyLoad(sourceController);
		int index = 0;
		if(connections.isEmpty())
		{
			EvalCollector.get().incrementFirstConnections();
		}
		else
		{
			for (int i = 0; i < connections.size(); i++)
			{
				ControllerConnection con = connections.get(i);
				if (costs < con.getCosts())
				{
					break;
				}
				index++;
			}			
			if(index == 0)
			{
				EvalCollector.get().incrementNewBestConection();
			}
		}

		connections.add(index, connection);
		System.out.println("Store connection to " + sourceController + " at " + connection.getSwitch().getId().getLong() + ":"
				+ connection.getPort().getPortNumber() + ", cost=" + costs);

		return index;
	}

	/*
	 * inform all neighboring clients about new (better) connection to
	 * sourceController
	 */
	private void broadcastDiscovery(int sourceController, IOFSwitch inSw, OFPort inPort, int costs) {
		int previousController = getPreviousController(inSw, inPort, sourceController);
		for (Integer connectedController : this.getConnectedController())
		{
			// do not send to source controller
			if (connectedController == sourceController || connectedController == previousController)
			{
				continue;
			}
			for (ControllerConnection c : this.getAllConnectionsTo(connectedController))
			{
				int additionalPathCosts = Routing.getPathCost(inSw, c.getSwitch());
				if(additionalPathCosts == -1) // no path exists
				{
					System.out.println("no path exists");
					continue;
				}
				byte[] discovery = OFMessageBuilder.discoveryPacket(costs + additionalPathCosts, sourceController, 1024,
						(int) inSw.getId().getLong());
				OFPacketOut msg = OFMessageBuilder.packetOut(c.getSwitch(), c.getPort(), discovery);
				c.getSwitch().write(msg);
				System.out.println("Broadcast to " + connectedController);
				EvalCollector.get().incrementDisoveryMessageCounter();
				System.out.println(
						"Forward discovery from " + sourceController + " to " + c.getSwitch().getId().getLong() + ":" + c.getPort().getPortNumber());
			}
		}
	}

	/**
	 * Send an activation message to the source controller of the discovery
	 * 
	 * @param foreignController
	 *            source controller of discovery, which is now target
	 * @param costs
	 * @param inPort
	 * @param sw
	 * @param costs
	 * @param connection
	 * @param costs
	 */
	private void activateConnection(int foreignController, IOFSwitch sw, OFPort port, int costs) {
		byte[] serializedData = OFMessageBuilder.activatePacket(this.controllerId, foreignController, costs);
		OFPacketOut po = OFMessageBuilder.packetOut(sw, port, serializedData);
		sw.write(po);
		EvalCollector.get().incrementActivationMessageCounter();
		System.out.println("Activation from this to " + foreignController + " at " + sw.getId().getLong() + ":" + port.getPortNumber());
	}

	/**
	 * install rule to allow incoming controller messages on this port
	 * 
	 * @param sw
	 *            on which connected to other controller
	 * @param inPort
	 *            for port which is destined pointed to other controller
	 */
	public void installLastHopCCRulePath(IOFSwitch sw, OFPort inPort) {
		OFFlowAdd modMsg = OFMessageBuilder.flowModControllerPath(sw, inPort, this.controllerId);
		sw.write(modMsg);
		EvalCollector.get().incrementRules();
	}

	/*
	 * find controller where a message came from
	 */
	private int getPreviousController(IOFSwitch inSwitch, OFPort inPort, int excludedController) {
		for (Integer connectedController : this.getConnectedController())
		{
			// do not send to source controller
			if (connectedController == excludedController)
				continue;
			for (ControllerConnection c : this.getAllConnectionsTo(connectedController))
			{
				if (c.getSwitch().getId().compareTo(inSwitch.getId()) == 0 && c.getPort().compareTo(inPort) == 0)
				{
					return connectedController;
				}
			}
		}
		return -1;
	}

	private List<ControllerConnection> lazyLoad(int foreignController) {
		if (!this.connections.containsKey(foreignController))
		{
			this.connections.put(foreignController, new LinkedList<>());
		}
		return this.connections.get(foreignController);
	}

	public boolean hasConnectionTo(int foreignControllerId) {
		return this.connections.containsKey(foreignControllerId) && !this.connections.get(foreignControllerId).isEmpty();
	}

	public boolean hasConnectionAt(int foreignControllerId, IOFSwitch sw, OFPort port) {
		if (this.connections.containsKey(foreignControllerId))
		{
			for(ControllerConnection c : this.connections.get(foreignControllerId))
			{
				if(c.getSwitch().getId().compareTo(sw.getId()) == 0 && c.getPort().compareTo(port) == 0)
					return true;
			}
		}
		return false;
	}

	public Set<Integer> getConnectedController() {
		return this.connections.keySet();
	}

	public ControllerConnection getConnectionTo(int controller) {
		return this.connections.get(controller).get(0);
	}

	public List<ControllerConnection> getAllConnectionsTo(Integer controller) {
		return this.connections.get(controller);
	}

}