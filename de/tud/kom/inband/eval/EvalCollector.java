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

package de.tud.kom.inband.eval;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class EvalCollector {

	private static EvalCollector instance = new EvalCollector();
	private List<Long> discoveries;
	private List<Long> activations;
	private List<Long> firstConnection;
	private List<Long> newBestConnection;
	private List<Long> intraNetworkDiscoveries;
	private List<Long> rules;
	private int controllerId;
	
	static{
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			
			@Override
			public void run() {
				EvalCollector.get().writeOut();
				System.out.println("Dump written");
			}
		}));
	}

	private EvalCollector() {
		this.discoveries = new LinkedList<>();
		this.activations = new LinkedList<>();
		this.firstConnection = new LinkedList<>();
		this.newBestConnection = new LinkedList<>();
		this.intraNetworkDiscoveries = new LinkedList<>();
		this.rules = new LinkedList<>();
	}

	public static EvalCollector get() {
		return instance;
	}

	public void setControllerId(int controllerId) {
		this.controllerId = controllerId;
	}

	public void incrementDisoveryMessageCounter() {
		discoveries.add(System.currentTimeMillis());
	}

	public void incrementActivationMessageCounter() {
		activations.add(System.currentTimeMillis());
	}

	public void incrementFirstConnections() {
		firstConnection.add(System.currentTimeMillis());
	}

	public void incrementNewBestConection() {
		newBestConnection.add(System.currentTimeMillis());
	}

	public void incrementIntraNetworkDiscoveries() {
		intraNetworkDiscoveries.add(System.currentTimeMillis());
	}
	
	public void incrementRules() {
		rules.add(System.currentTimeMillis());
	}

	public String getDiscoveries() {
		return Arrays.toString(discoveries.toArray());
	}

	public String getActivations() {
		return Arrays.toString(activations.toArray());
	}

	public String getFirstConnection() {
		return Arrays.toString(firstConnection.toArray());
	}

	public String getNewBestConnection() {
		return Arrays.toString(newBestConnection.toArray());
	}

	public String getIntraNetworkDiscoveries() {
		return Arrays.toString(intraNetworkDiscoveries.toArray());
	}
	
	public String getRules() {
		return Arrays.toString(rules.toArray());
	}


	public void writeOut() {
		try
		{
			File parent = new File("dumps/run_" + (int) (System.currentTimeMillis() / 1000));
			parent.mkdirs();
			File file = new File(parent, "controller_" + this.controllerId);// + "_" + (int) (System.currentTimeMillis() / 1000));
			BufferedWriter writer = new BufferedWriter(
					new FileWriter(file));
			StringBuffer str = new StringBuffer();
			str.append("controller=").append(controllerId);
			str.append("\ndiscoveries=").append(discoveries);
			str.append("\nactivations=").append(activations);
			str.append("\nfirstconnections=").append(firstConnection);
			str.append("\nnewBest=").append(newBestConnection);
			str.append("\nintradiscoveries=").append(intraNetworkDiscoveries);
			str.append("\nrules=").append(rules);
			writer.write(str.toString());
			writer.flush();
			writer.close();
			System.out.println("Dump written to " + file.getAbsolutePath());
		} catch (IOException e)
		{
			e.printStackTrace();
		}
	}
}
