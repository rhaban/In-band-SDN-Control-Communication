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

import org.projectfloodlight.openflow.types.OFPort;

import net.floodlightcontroller.core.IOFSwitch;

public class ControllerConnection {
	IOFSwitch switch_;
	OFPort port;
	int costs;

	public ControllerConnection(IOFSwitch switch_, OFPort port, int costs) {
		super();
		this.switch_ = switch_;
		this.port = port;
		this.costs = costs;
	}

	public IOFSwitch getSwitch() {
		return switch_;
	}

	public void setSwitch_(IOFSwitch switch_) {
		this.switch_ = switch_;
	}

	public OFPort getPort() {
		return port;
	}

	public void setPort(OFPort port) {
		this.port = port;
	}

	public int getCosts() {
		return costs;
	}

	public void setCosts(int costs) {
		this.costs = costs;
	}

}