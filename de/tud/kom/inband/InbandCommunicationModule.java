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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.DatapathId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tud.kom.inband.eval.EvalCollector;
import de.tud.kom.inband.service.ChatApplication;
import de.tud.kom.inband.service.ControllerCommunicationService;
import de.tud.kom.inband.util.ControllerConnection;
import de.tud.kom.inband.util.OFMessageBuilder;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.Ethernet;

public class InbandCommunicationModule implements IOFMessageListener, IFloodlightModule, ControllerCommunicationService, IOFSwitchListener {

	protected IFloodlightProviderService floodlightProvider;
	private IOFSwitchService switchService;
	protected Set<Long> macAddresses;
	private MessageHandler messageHandler;
	private ConnectionManager connectionManager;
	private Set<MessageListener> listeners;
	private int controllerId;
	protected static Logger logger;

	@Override
	public String getName() {
		return this.getClass().getSimpleName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return false;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
	    Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
	    l.add(ControllerCommunicationService.class);
	    return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
	    Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
	    m.put(ControllerCommunicationService.class, this);
	    return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IOFSwitchService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);
		logger = LoggerFactory.getLogger(InbandCommunicationModule.class);
		
		Map<String, String> configOptions = context.getConfigParams(this);
        this.controllerId = Integer.parseInt(configOptions.get("id"));
        EvalCollector.get().setControllerId(this.controllerId);
        this.listeners = new HashSet<>();
		
        this.connectionManager = new ConnectionManager(controllerId);
		this.messageHandler = new MessageHandler(controllerId, this.connectionManager, this.switchService);
		
		// test module
		ChatApplication chat = new ChatApplication(); 
		this.registerMessageListener(chat);
		chat.testCommunication(this);
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		System.out.println("startUp");
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		switchService.addOFSwitchListener(this);
		keepAlive();
	}

	private void keepAlive() {
		new Thread(){
			public void run(){
				for(;;)
				{
					try
					{
						Thread.sleep(OFMessageBuilder.IDLE_TIMEOUT*750); // was in seconds, now milliseconds and only 3/4
						for (Iterator<Integer> i = getConnectedController().iterator(); i.hasNext();)
						{
							int c = (int) i.next();
							sendMessageToController(c, "keep_alive");
						}
					} catch (InterruptedException e)
					{
						e.printStackTrace();
					}
				}
			}
		}.start();
	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(IOFSwitch sw, OFMessage msg,
			FloodlightContext cntx) {
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

		if(eth.getSourceMACAddress().compareTo(OFMessageBuilder.DISCOVERY_MAC) == 0) {
			this.messageHandler.processDiscovery(sw, msg, eth);
		}
		else if (eth.getSourceMACAddress().compareTo(OFMessageBuilder.ACTIVATE_MAC) == 0) {
			this.messageHandler.processActivation(sw, msg, eth);
		}
		else if (eth.getVlanID() == OFMessageBuilder.OF_VLAN_COMMUNICATION.getVlan())
		{
			this.messageHandler.processCommunicationMessage(eth, listeners);
		}
		
		return Command.CONTINUE;
	}

	@Override
	public void switchAdded(DatapathId switchId) {
		System.out.println("switchAdded " + switchId.toString());
		IOFSwitch activeSwitch = this.switchService.getActiveSwitch(switchId);
		OFFlowAdd flowAdd = OFMessageBuilder.flowModDiscovery(activeSwitch);
		activeSwitch.write(flowAdd);
	}

	@Override
	public void switchPortChanged(DatapathId switchId, OFPortDesc port, PortChangeType type) {
		switch(type) {
		case ADD:
			break;
		case DELETE:
			break;
		case DOWN:
			// TODO
			break;
		case OTHER_UPDATE:
			break;
		case UP:
			IOFSwitch switch_ = this.switchService.getSwitch(switchId);
			this.connectionManager.discoverPort(switch_, port.getPortNo());
			break;
		default:
			break;
		
		}
	}

	@Override
	public void switchRemoved(DatapathId switchId) {}

	@Override
	public void switchActivated(DatapathId switchId) {}

	
	@Override
	public void switchChanged(DatapathId switchId) {}

	@Override
	public void switchDeactivated(DatapathId switchId) {}

	@Override
	public Set<Integer> getConnectedController() {
		return this.connectionManager.getConnectedController();
	}

	/**
	 * service functions
	 */
	@Override
	public boolean sendMessageToController(int controller, String message) {
		if(!this.connectionManager.hasConnectionTo(controller))
		{
			return false;
		}
		byte[] msg = OFMessageBuilder.controllerMessage(this.controllerId, controller, message);
		ControllerConnection connection = this.connectionManager.getConnectionTo(controller);
		OFPacketOut packetOut = OFMessageBuilder.packetOut(connection.getSwitch(), connection.getPort(), msg);
		return connection.getSwitch().write(packetOut);
	}

	@Override
	public void registerMessageListener(MessageListener l) {
		this.listeners.add(l);
	}
}





