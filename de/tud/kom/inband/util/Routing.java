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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.types.OFPort;

import de.tud.kom.inband.eval.EvalCollector;
import net.floodlightcontroller.core.IOFSwitch;

// FIXME: so far limited to one-hop
public class Routing {

	private static Map<Long, Set<Port>> intraNetworkLinks = new HashMap<>();

	public static List<Port> getPathOld(IOFSwitch srcSw, IOFSwitch dstSw) {
		if(!intraNetworkLinks.containsKey(srcSw.getId().getLong()))
			return null;
		for (Port p : intraNetworkLinks.get(srcSw.getId().getLong()))
		{
			if (p.switch_.getId().compareTo(dstSw.getId()) == 0)
			{
				return Collections.singletonList(p);
			}
		}
		return null;
	}
	
	public static List<Port> getPath(IOFSwitch srcSw, IOFSwitch dstSw) {
		if(!intraNetworkLinks.containsKey(srcSw.getId().getLong()))
			return null;
		
		for (Port p : intraNetworkLinks.get(srcSw.getId().getLong()))
		{
			if (p.switch_.getId().compareTo(dstSw.getId()) == 0)
			{
				return Collections.singletonList(p);
			}
		}
		return null;
	}

	public static int getPathCost(IOFSwitch srcSw, IOFSwitch destSw) {
		if (srcSw.getId().compareTo(destSw.getId()) == 0)
			return 1;

		List<Port> path = getPath(srcSw, destSw);
		return path != null ? path.size() : -1;
	}

	public static synchronized void addIntraNetworkLink(IOFSwitch fromSwitch, OFPort fromPort, IOFSwitch toSwitch) {
		long from = fromSwitch.getId().getLong();
		OFPort port = fromPort;
		if (!intraNetworkLinks.containsKey(from))
		{
			intraNetworkLinks.put(from, new HashSet<>());
		}
		Set<Port> set = intraNetworkLinks.get(from);
		if(set == null)
		{
			System.err.println("Set was null for " + from + ":" + intraNetworkLinks.get(from));
		}
		set.add(new Port(toSwitch, port));
	}

	public static Port installRulesBetween(int sourceController, IOFSwitch inSwitch, OFPort inPort, int targetController, IOFSwitch outSwitch, OFPort outPort) {
		if (inSwitch.getId().compareTo(outSwitch.getId()) == 0)
		{
			OFFlowAdd ofFlowMod = OFMessageBuilder.flowModIntermediateControllerPath(sourceController, inPort, targetController, outSwitch, outPort);
			outSwitch.write(ofFlowMod);
			EvalCollector.get().incrementRules();
		} else
		{

			Port p_forward = null, p_reverse = null;
			// forward
			for (Port p : intraNetworkLinks.get(inSwitch.getId().getLong()))
			{
				if (p.switch_.getId().compareTo(outSwitch.getId()) == 0)
				{
					p_forward = p;
				}
			}
			// reverse (to find port)
			for (Port q : intraNetworkLinks.get(outSwitch.getId().getLong()))
			{
				if (q.switch_.getId().compareTo(inSwitch.getId()) == 0)
				{
					p_reverse = q;
				}
			}

			if (p_forward == null || p_reverse == null)
				return null;

			OFFlowAdd ofFlowModInner = OFMessageBuilder.flowModIntermediateControllerPath(sourceController, inPort, targetController, inSwitch, p_forward.port);
			OFFlowAdd ofFlowModOut = OFMessageBuilder.flowModIntermediateControllerPath(sourceController, p_reverse.port, targetController, outSwitch, outPort);
			inSwitch.write(ofFlowModInner);
			outSwitch.write(ofFlowModOut);
			EvalCollector.get().incrementRules();
			EvalCollector.get().incrementRules();
		}
		return new Port(outSwitch, outPort);
	}

	/**
	 * check whether the switch:port is used in a path between other controllers 
	 * @param switchService 
	 * 
	 */
//	public static Set<Integer> intermediateRouteUsingPort(ConnectionManager connectionManager, IOFSwitchService switchService, IOFSwitch switch_, OFPort port) {
//		Set<Integer> connectableControllers = new HashSet<>();
//		for(Integer c1 : connectionManager.getConnectedController())
//		{
//			for(Integer c2: connectionManager.getConnectedController())
//			{
//				if(c1 == c2)
//					continue;
//				IOFSwitch toC1 = switchService.getSwitch(DatapathId.of(c1));
//				IOFSwitch toC2 = switchService.getSwitch(DatapathId.of(c2));
//				List<Port> path = getPath(toC1, toC2);
//				if(path == null)
//					continue;
//				for(Port p : path)
//				{
//					if(p.getSwitch_().getId().compareTo(switch_.getId()) == 0 && p.getPort().compareTo(port) == 0)
//					{
//						connectableControllers.add(c2);
//					}
//				}
//			}
//		}
//		return connectableControllers;
//	}

}
