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

package de.tud.kom.inband.service;

import java.util.Scanner;

import de.tud.kom.inband.eval.EvalCollector;
import de.tud.kom.inband.service.ControllerCommunicationService.MessageListener;

public class ChatApplication implements MessageListener {

	public void testCommunication(ControllerCommunicationService service) {
		new Thread() {

			public void run() {
				@SuppressWarnings("resource")
				Scanner s = new Scanner(System.in);
				while (true) {
					try {
						String line = s.nextLine();
						if(line.equals("exit"))
						{
							EvalCollector.get().writeOut();
							System.out.println("Shutdown Controller");
							System.exit(0);
						}
						int index = line.indexOf(':');
						int controller = Integer.parseInt(line.substring(0, index));
						String message = line.substring(index + 1);
						boolean success = service.sendMessageToController(controller, message);
						if (!success)
							throw new Exception("returned false");
					} catch (Exception e) {
						System.err.println("Could not parse input. (" + e.getMessage() + ")");
					}
				}
			}
		}.start();
	}

	@Override
	public void onMessage(int dispatcher, String message) {
		if(message.equals("keep_alive"))
			return;
		System.out.println("Chat " + dispatcher + ": \"" + message + "\"");
	}
}
