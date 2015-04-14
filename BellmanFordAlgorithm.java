import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

/**
 * jfang 12/2013 Computer Networking Programming 3
 **/
public class BellmanFordNode {
	
		private static ArrayList<DistanceVector> node = null;  				//local DV to all destination
		private static ArrayList<ArrayList<DistanceVector>> neighbor = null;//all neighbors' DVs
		//private static ArrayList<TimerTable> TimerList = null;
		private static final double INF = 20000000;//Double.POSITIVE_INFINITY;
		private static DistanceVector localVector;
		private static ArrayList<NodeTimer> nodeAddress = null;
		private static int timeOut;
		private static Timer sendTimer;
		private static int WPORT;
		private static int neighborNr = 0;
		private static int listenPort;
		private static String localIP = null;
		private static final int PAYLOAD = 1000;
		private static boolean notClose = true;
		
		private static int cIndex = 0;
		private static ArrayList<Discard> dList = null;
		
		public static void main(String args[]) throws IOException {

			if((args.length - 3)%3 != 0){
				System.out.println("invalid format");
				return;
			}
			String input[] = null;
			localIP = args[0];
			listenPort = Integer.parseInt(args[1]);
			timeOut = Integer.parseInt(args[2]);
			
			neighborNr = (args.length - 3) / 3;
			
			DatagramSocket senderSocket = new DatagramSocket(WPORT);
			DatagramSocket serverSocket = new DatagramSocket(listenPort);
			WPORT = 1024 + (int)(Math.random() * ((65535 - 1024) + 1));
			
			node = new ArrayList<DistanceVector>();
			nodeAddress = new ArrayList<NodeTimer>();
			neighbor = new ArrayList<ArrayList<DistanceVector>>();
			dList = new ArrayList<Discard>();
			
		         
			input = args;
			//BellmanFordNode BellNode = 
			
			BellmanFordNode BellNode = new BellmanFordNode(input,senderSocket,serverSocket);
			//BellNode.firstUpdate(args);		
		}
		
		
		BellmanFordNode(String input[],DatagramSocket sender, DatagramSocket receiver) throws IOException{
			localVector = new DistanceVector(localIP,listenPort,0);
			node.add(localVector);
			InitNeighbor init = new InitNeighbor(input, sender);
			init.initDistanceVector();
			//firstUpdate(args,sender);
			
			ReceiveThread receiveThread = new ReceiveThread(receiver);
			receiveThread.start();
			InputThread inputThread = new InputThread(sender);
			inputThread.start();
		}
		
		class Discard{
			private String ip;
			private int port;
			
			Discard(String ip, int port){
				this.ip = ip;
				this.port = port;
			}
		}
		
		public void routeFromOther(String dip, int dport, int nodeIdx){
			int idx;
			double lCost;
			DistanceVector lDV = null;
			lDV = node.get(nodeIdx);
			lCost = lDV.getCost();
			
			
			for(int i = 0; i < neighbor.size(); i ++){
				
				double costToN = nodeAddress.get(i).getCost();
				if(nodeAddress.get(i).isBroken()){
					continue;
				}
				ArrayList<DistanceVector> adv = new ArrayList<DistanceVector>();
				adv = neighbor.get(i);
				String nbIP = adv.get(0).getIP();
				int nbPort = adv.get(0).getPort();
				int k;
				
				
				
				for(int j = 0; j < adv.size(); j ++){
					DistanceVector dv = null;
					dv = adv.get(j);
					if(dv.getIP().equals(dip) && dv.getPort() == dport &&( dv.getCost() + costToN <= lCost)){
						lCost = dv.getCost() + costToN;
						lDV.setCost(lCost);
						lDV.setNextHop(nbIP,nbPort);
						node.set(nodeIdx, lDV);
					}
				}
			}
			
		}
		class CheckBreak extends TimerTask{
			private int index;
			private boolean justReceive;
			
			public void justReceived(){
				this.justReceive = true;
				//System.out.println("justreceived in checkbreak renew" + justReceive);
			}
			public CheckBreak(int i){
				this.index = i;
				this.justReceive = true;
			}
			
			public void run(){
				//System.out.println("just received " + justReceive);
				NodeTimer tempNT = nodeAddress.get(index);
				String iIP = tempNT.getIP();
				int iPort = tempNT.getPort();
				if(justReceive == false){
					tempNT.setBroken();
					System.out.println("break node " + tempNT.getPort());
					//System.out.println("dList size " + dList.size());
					for(int i = 0; i < dList.size(); i ++){
						System.out.println("dList element " + dList.get(i).port);
					}
					nodeAddress.set(index, tempNT);
					
					
					for(int i = 1; i < node.size(); i ++){
						DistanceVector d = null;
					    d = node.get(i);
					    
					    String desti = d.getIP();
					    int destp = d.getPort();
					    
					    if(desti.equals(iIP) && destp == iPort){
					    	d.setCost(INF);
					    	node.set(i, d);
					    	continue;
					    }
					    
					}
					
				}
				else{
					justReceive = false;
				}
				
			}
		}
				
		
		public int searchNode(String ip, int port){
			for(int i = 0; i < nodeAddress.size(); i ++){
				if(nodeAddress.get(i).exist(ip, port))
					return i;
			}
			return -1;
		}
		
		class ReceiveThread extends Thread{
			private DatagramSocket receiveSocket;
			private DatagramPacket receivePacket = null;
			private String tmIP;
		    private int tmPort;
		    private double tmCost;
		    private String tmHopIP;
			private int tmHopPort;
		    
		    
			ReceiveThread(DatagramSocket socket){
				this.receiveSocket = socket;
				
			}
		
			public void UpdateCost(ArrayList<String> DataReceived, int place){
				/*received a packet from neighbor A, then update neighbor A's distance vector
				 in local storage. Get the specific array list out, check whether A has changes
				 if it has. Local storage should also change
				 */
				
				boolean dvExist = false;
				boolean changed = false;
				ArrayList<DistanceVector> ud = new ArrayList<DistanceVector>();
				//get the neighbor whose DV need to be checked whether to update it
				//according to the information received from this neighbor, update it and write it back to the 
				//original neighbor-distanceVector array list
				//when receiving new node from the neighbor, add it to the distance vector
				ud = neighbor.get(place);
				
				String nsIP = ud.get(0).getIP();
				int nsPort = ud.get(0).getPort();
				//System.out.println("****" + ud.get(0).getCost());
				//System.out.println("****" + nodeAddress.get(place).getCost() + "to port " + nodeAddress.get(place).getPort());
				
				String udIP = null;
				int udPort;
				double udCost;
				
				//System.out.println("Received data size " + DataReceived.size());
				
			    for(int i = 1; i < DataReceived.size(); i ++){
			    	
			    	String DataLine = DataReceived.get(i);
			    	//System.out.println("UpdateCost  " + DataLine + "  " + i);
				    String[] split = DataLine.split("[;]");
				
				    tmIP = split[0];
				    tmPort = Integer.parseInt(split[1]);
				    tmCost = Double.parseDouble(split[2]);
				    tmHopIP = split[3];
				    tmHopPort = Integer.parseInt(split[4]);
					//System.out.println("node " + nsPort + "see dest " + tmPort + "through  " + tmHopPort);
				
				dvExist = false;
				for(int j = 1; j < ud.size(); j ++){
					udIP = ud.get(j).getIP();
					udPort = ud.get(j).getPort();
					if(udIP.equals(tmIP) && udPort == tmPort){
						
						dvExist = true;
							//-----------poisoned reverse
						if((tmHopIP.equals(localIP) && tmHopPort == listenPort) && !(tmIP.equals(localIP) && tmPort == listenPort)){
								tmCost = INF;
						}
						DistanceVector update = new DistanceVector(udIP, udPort, tmCost, tmHopIP, tmHopPort);
						ud.set(j, update);
						break;
					}
					
				}
				
				if(!dvExist){
					ud.add(new DistanceVector(tmIP, tmPort, tmCost, tmHopIP, tmHopPort));
				}
			}
			    	neighbor.set(place,ud);
			    	updateTable(place);
			    
			}
			
			public void run(){
				try{
					
					//socketReader = new BufferedReader(new InputStreamReader(receiveSocket.getInputStream()));
		    		
			    	while(!receiveSocket.isClosed() && notClose){
			    		byte[] receiveData = new byte[PAYLOAD];
			    		
			    		receivePacket = new DatagramPacket(receiveData, receiveData.length);
			    		receiveSocket.receive(receivePacket);
			    		
			    		
			    		String str = new String(receiveData, "UTF-8");
			    		BufferedReader bufReader = new BufferedReader(new StringReader(str));
			    		String line = null;
			    		ArrayList<String> receiveList = new ArrayList<String>();
			    		
			    		
			    		//int ct = 0;
			    		while( (line=bufReader.readLine()) != null ){
			    			if(line.split("[;]").length == 5){
			    			//System.out.println(line + ct ++);
			    			receiveList.add(line);
			    			}
			    			else{
			    				String[] cSplit = line.split("[;]");
			    				if(cSplit.length == 3){
			    					
			    					if(cSplit[0].equals("LINKDOWN")){
			    						String downIP = cSplit[1];
			    						int downPort = Integer.parseInt(cSplit[2]);
			    						int idx, ix;
			    						
			    						dList.add(new Discard(downIP, downPort));
			    						
			    						System.out.println("LINK DOWN received");
			    						if((ix = searchNode(downIP, downPort)) != -1){
			    							NodeTimer nt = nodeAddress.get(ix);
			    							nt.setBroken();
			    							nodeAddress.set(ix, nt);
			    							System.out.println("neighbor set" + nt.broken);
			    						}
			    						
			    						if((idx = searchLocalNode(downIP, downPort))!= -1){
			    							DistanceVector dv = null;
			    							dv = node.get(idx);
			    							
			    							dv.setInfinite();
			    							node.set(idx, dv);
			    						}
			    						
			    						for(int i = 0; i < node.size(); i ++){
				    						DistanceVector d = null;
				    						System.out.println(node.size());
				    					    d = node.get(i);
				    					    String bni = d.getNextHop();
				    					   
				    					    int bnp = d.getNextPort();
				    					   
				    					    String desti = d.getIP();
				    					    int destp = d.getPort();
				    					    if(bni.equals(downIP) && bnp == downPort){
				    					    	System.out.println("route from others");
				    					    }
			    						
			    						}
			    					}
			    					
			    					else if(cSplit[0].equals("LINKUP")){
			    						String upIP = cSplit[1];
			    						int upPort = Integer.parseInt(cSplit[2]);
			    						
			    						for(int r = 0; r < dList.size(); r ++){
			    							if(dList.get(r).ip.equals(upIP) && dList.get(r).port == upPort)
			    								dList.remove(r);
			    						}
			    						//System.out.println("dList size " + dList.size());
			    						//for(int i = 0; i < dList.size(); i ++){
			    							//System.out.println("dList element " + dList.get(i).port);
			    						//}
			    						int ix,idx;
			    						if((ix = searchNode(upIP, upPort)) != -1){
			    							NodeTimer nt = nodeAddress.get(ix);
			    							nt.setConnect();
			    							nodeAddress.set(ix, nt);
			    							System.out.println("neighbor set" + nt.broken);
			    						}
			    						
			    						if((idx = searchLocalNode(upIP, upPort))!= -1){
			    							DistanceVector dv = null;
			    							dv = node.get(idx);
			    							
			    							dv.setFinite();
			    							node.set(idx, dv);
			    						}
			    						
			    						for(int i = 0; i < node.size(); i ++){
				    						DistanceVector d = null;
				    						System.out.println(node.size());
				    					    d = node.get(i);
				    					    String bni = d.getNextHop();
				    					   
				    					    int bnp = d.getNextPort();
				    					   
				    					    String desti = d.getIP();
				    					    int destp = d.getPort();
				    					    if(bni.equals(upIP) && bnp == upPort){
				    					    	System.out.println("route from others");
				    					    }
			    						
			    						}
			    					}
			    				}
			    			
			    			}
			    		}
			    		
			    		
			    		//System.out.println("run receiveList size  " + receiveList.size());
			    		
			    		if(receiveList.size() > 0){
			    			//System.out.println("has receiveList");
							String[] firstSplit = receiveList.get(0).split("[;]");				
							StringBuffer buffer =  new StringBuffer();
							//System.out.println("receiveThread " + firstSplit[0] +" " + firstSplit[1] );
							int index;
							String rIp = firstSplit[0];
							int rPort = Integer.parseInt(firstSplit[1]);
							boolean discarded = false;
							
							for(int d = 0; d < dList.size(); d ++){
								if(dList.get(d).ip.equals(rIp) && dList.get(d).port == rPort){
				    					discarded = true;
				    					break;
								}
							}
							if(!discarded){
								//System.out.println("not discarded");
							if((index = searchNode(firstSplit[0],Integer.parseInt(firstSplit[1]))) != -1){
								
								UpdateCost(receiveList,index);
								//if(nodeAddress.get(index).broken){
								
								nodeAddress.get(index).setConnect();
								//System.out.println("receive packet see broken" + nodeAddress.get(index).broken);
								//}
							}
							else addNewNeighbor(receiveList,firstSplit[0],Integer.parseInt(firstSplit[1]));
							
				    		}
			    		}	
			    		//else
			    			
						}
			    	} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}finally{
						receiveSocket.close();
				        
			    	}

		}
		
		}
		
		
		public void addNewNeighbor(ArrayList<String> DataReceived, String ip, int port){
			String tmIP = null;
		    int tmPort = 0;
		    double tmCost;
		    String tmHopIP;
			int tmHopPort;
			ArrayList<DistanceVector> newNeighbor = new ArrayList<DistanceVector>();
			
			for(int i = 0; i < DataReceived.size() ; i ++){
				String DataLine = DataReceived.get(i);
			    String[] split = DataLine.split("[;]");
				
				tmIP = split[0];
				tmPort = Integer.parseInt(split[1]);
				tmCost = Double.parseDouble(split[2]);
				tmHopIP = split[3];
				tmHopPort = Integer.parseInt(split[4]);
				
				if(tmIP.equals(localIP) && tmPort == listenPort){
					DistanceVector sNew = new DistanceVector(ip, port,tmCost,ip,port);
					node.add(sNew);   
					nodeAddress.add(new NodeTimer(ip, port, cIndex,tmCost));
					cIndex ++;
					break;
					//neighborCost = tmCost;
				}
				DistanceVector newDV = new DistanceVector(tmIP, tmPort, tmCost, tmHopIP, tmHopPort);
				newNeighbor.add(newDV);
			}
			neighbor.add(newNeighbor);
			//nodeAddress.add(new NodeTimer(tmIP, tmPort,cIndex));
			
			int place = neighbor.size() - 1;
			updateTable(place);
			
		}
		
		public void updateTable(int place){
			//updateTable means neighbor has been added to neighbor array list
			//So the local DistanceVector array should also has this neighbor's record
			//Or when local node see changes in neighbor's distance vector, local node updates also
				ArrayList<DistanceVector> nDV = new ArrayList<DistanceVector>();
				boolean newDest = true;
				double newNeighborCost;
				//boolean hasCost = false;
				nDV = neighbor.get(place);
				String nsIP = nDV.get(0).getIP();
				int nsPort = nDV.get(0).getPort();
				if(nsPort == 1111 ){
					//System.out.println("update from 1");
				}
				
				int index;
				double cost = INF;
				
				if((index = searchLocalNode(nsIP, nsPort)) != -1){
					cost = nodeAddress.get(place).getCost();
					if(nsPort == 1111 ){
						//System.out.println("cost to 1111" + cost);
						
					}
				}
				
				 /* start: add new neighbor if not exist*/
				 
				else{
					for(int i = 0; i < nDV.size(); i ++){
						if(nsIP.equals(localIP) && nsPort == listenPort){
							newNeighborCost = nDV.get(i).getCost();
							DistanceVector dv = new DistanceVector(nsIP, nsPort, newNeighborCost, nsIP, nsPort);
							node.add(dv);
							break;
						}
					}
					
				}
				/*end: add new neighbor*/
				/*start: add new destination*/
				//find the cost between neighbor and local
				//for each point in neighbor's forwarding table, find it in local
				for(int i = 1; i < nDV.size() ; i ++){
					newDest = true;
					String nIP = nDV.get(i).getIP();
					int nPort = nDV.get(i).getPort();
					Double nCost = nDV.get(i).getCost();
					//the cost to the destination from neighbor seen by local
					
					//String nHop;
					if(nsPort == 1111 && nPort == 4444){
						//System.out.println("cost from 1111 to 4444" + nCost);
					}
					
					for(int j = 0; j < node.size(); j ++){
						DistanceVector sDV = null;                 //////////////
						sDV = node.get(j);
						String sIP = node.get(j).getIP();
						int sPort = node.get(j).getPort();
						double sCost = node.get(j).getCost();
						
						if(sPort == 4444){
							//System.out.println("init cost to 4444 " + sCost);
						}
						
						if(sIP.equals(nIP) && sPort == nPort){
							newDest = false;
							break;
							/*if(sCost > INF/2){
								break;
							}*/
							/*if (sCost >= nCost + cost){
							
							sDV.setCost(nCost + cost);
							sDV.setNextHop(nsIP, nsPort);
							node.set(j, sDV);
							break;
						    }*/
						}
					}
					if(newDest){
						node.add(new DistanceVector(nIP,nPort,nCost + cost, nsIP, nsPort));
					}
			}/*end:add new destination*/
				
				/*start: calculate min cost for each node in local routing table*/
				for(int i = 0; i < node.size(); i ++){
					int minCounter;
					DistanceVector sDV = null;                 //////////////
					sDV = node.get(i);
					String sIP = sDV.getIP();
					int sPort = sDV.getPort();
					String sNextIP = sDV.getNextHop();
					int sNextPort = sDV.getNextPort();
					double minCost = sDV.getCost();
					
					//double sCost = 2*INF;
					
					
					double InfiMinCost = 2*INF;
					String InfiMinCostNextIP = null;
					int InfiMinCostNextPort = 0;
					
					String minCostNextIP = null;
					int minCostNextPort = 0;
					minCounter = 0;
					boolean pathChanged = false;
					for(int j = 0; j < nodeAddress.size(); j ++){
						double sCost = node.get(i).getCost();
						ArrayList<DistanceVector> neighborDV = new ArrayList<DistanceVector>();
						neighborDV = neighbor.get(j);
						double costToNeighbor = nodeAddress.get(j).getCost();
						
							//System.out.println("see from neighbor " + nodeAddress.get(j).getPort());
						
						if(nodeAddress.get(j).isBroken()){
							continue;
						}
						
						for(int k = 0; k < neighborDV.size(); k ++){
							String nIP = neighborDV.get(k).getIP();
							int nPort = neighborDV.get(k).getPort();
							
								//System.out.println(nPort + " " + neighborDV.get(k).getCost() + " " + neighborDV.get(k).getNextPort());
							
							if(nIP.equals(sIP) && nPort == sPort){
								int idx;
								double NeighborCostToDest = neighborDV.get(k).getCost();
								/*if(NeighborCostToDest < INF){
									finite = true;
									System.out.println("neighbor cost to dest finite");
								}*/
							
								
								if(NeighborCostToDest + costToNeighbor <= minCost){
									
									pathChanged = true;
									minCost = NeighborCostToDest + costToNeighbor;
									
									if(listenPort == 3333 && sPort == 4444){
										//System.out.println("cost addition" + NeighborCostToDest + costToNeighbor);
										//System.out.println("self cost " + minCost);
									}
									
									minCostNextIP = neighborDV.get(0).getIP();
									minCostNextPort = neighborDV.get(0).getPort();
									//System.out.println("Update table " + sPort + " " + minCost);
									//System.out.println("Update table " + sPort + " " + minCostNextPort);
									sDV.setCost(minCost);
									sDV.setNextHop(minCostNextIP, minCostNextPort);
									node.set(i,sDV);
									if(listenPort == 3333 && sPort == 4444){
										//System.out.println("&&&&&&&&&&upDated cost to port 4444 " + node.get(i).getCost());
									}
								}
								
								else if(NeighborCostToDest + costToNeighbor < InfiMinCost){
										InfiMinCost = NeighborCostToDest + costToNeighbor;
										InfiMinCostNextIP = neighborDV.get(0).getIP();
										InfiMinCostNextPort = neighborDV.get(0).getPort();
										//System.out.println(":::Update table " + sPort + " " + minCost);
										//System.out.println(":::Update table " + sPort + " " + minCostNextPort);
									}
							
							
							}
						}
					}
					
					if(!pathChanged){
						
						if(!((sIP.equals(sNextIP))&&(sPort == sNextPort))){
							if(listenPort == 3333){
								//System.out.println("Update table: local value is the largest to " + sDV.getPort() + InfiMinCost );
							}
							
							sDV.setCost(InfiMinCost);
							sDV.setNextHop(InfiMinCostNextIP, InfiMinCostNextPort);
							node.set(i,sDV);
						}
						else{
							int idx;
							if((idx = searchNode(sIP,sPort)) != -1){
							NodeTimer neighborNT = nodeAddress.get(idx);
							double costToNei = neighborNT.getCost();
							String NextIP = neighborNT.getIP();
							int NextPort = neighborNT.getPort();
							
							sDV.setCost(costToNei);
							sDV.setNextHop(NextIP, NextPort);
							node.set(i,sDV);
							if(listenPort == 3333 && sPort == 4444){
								//System.out.println("+++++++++upDated cost to port 4444 " + node.get(i).getCost());
							}
							}
						}
					}
					//current cost is the smallest
					
				}
				/*end:calculate min cost for each node in local routing table*/
		}
		class ResendTimeTask extends TimerTask{
		
			private int neighborCount;
			private DatagramSocket socket;
			private byte[] sendData = new byte[PAYLOAD];
			private NodeTimer ipPort = null;
			private DatagramPacket tempPacket = null;
			private boolean lastsend = false;
			private int count = 0;
			
			public ResendTimeTask(DatagramSocket sendSocket){
				this.socket = sendSocket;
				this.neighborCount = neighbor.size();
			}
			
			public void run(){
				try{
					//System.out.println("time out resending " + counter);
					//counter ++;
					StringBuffer buffer = new StringBuffer();
					DistanceVector dv = null;
					for(int idx = 0; idx < node.size(); idx ++){
						dv = node.get(idx);
						buffer.append(dv.getIP()).append(";").append(dv.getPort()).append(";").append(dv.getCost()).append(";");
						buffer.append(dv.getNextHop()).append(";").append(dv.getNextPort()).append("\n");
					}
					
					sendData = buffer.toString().getBytes();
					neighborCount = nodeAddress.size();
					for(int k = 0; k < neighborCount; k ++){
						ipPort = nodeAddress.get(k);
						//System.out.println("resendtime task  " + nodeAddress.get(k).broken + "  " + k);
						if(nodeAddress.get(k).broken){    //when link to neighbor is broken send nothing
							count ++;
							if(count >= neighborCount){
								lastsend = true;
							}
							if(lastsend)
							   continue;
						}
						tempPacket = new DatagramPacket(sendData, sendData.length,InetAddress.getByName(ipPort.getIP()),ipPort.getPort());
						//linkCostUpdate();
						socket.send(tempPacket);
						//System.out.println("resend time task " + buffer.toString());
						//System.out.println("Send to neighbor " + ipPort.getPort());
					}
				
				}catch ( IOException e){
					e.printStackTrace();
				}
			}
		}

		public int searchLocalNode(String ip, int port){
			for(int i = 0; i < node.size(); i ++){
				if(node.get(i).exist(ip, port))
					return i;
			}
			return -1;
		}
		
		public void linkCostUpdate(){
			for(int i = 0; i < node.size(); i ++){
				if(node.get(i).isFirstHop())
					continue;
				
				DistanceVector dv = node.get(i);
				String ipHop = dv.getNextHop();
				int portHop = dv.getNextPort();
				
				for(int j = 0; j < node.size(); j ++){
					int index;
					if((index = searchLocalNode(ipHop, portHop)) != -1){
						if(node.get(index).getCost() == INF){
							/////////////System.out.println(node.get(index).getCost());
							dv.setInfinite();
							node.set(i, dv);
						}
					}
				}
			}
		}
		
		class InputThread extends Thread{
			private DatagramSocket socket = null;
			
			public InputThread(DatagramSocket socket){
				this.socket = socket;
			}
			
			public void processCommand(String userCommand) throws IOException{
				String[] split= userCommand.split("[ ]");
				String command = split[0];
				
				if(command.equals("LINKDOWN")){
					if(split.length != 3){
						System.out.println("wrong command");
						return;
					}
					
					String ip = split[1];
					int port = Integer.parseInt(split[2]);
					dList.add(new Discard(ip, port));
					System.out.println("dList added");
					int index;
					int nIndex;
					if((index = searchNode(ip,port)) != -1){
						NodeTimer nt = null;
						nt = nodeAddress.get(index);
						
						nt.setBroken();
						nodeAddress.set(index,nt);
						
						DistanceVector dv = null;
						int idx;
						
						for(int i = 0; i < node.size(); i ++){
							dv = node.get(i);
							if((dv.getIP().equals(ip) && dv.getPort() == port)||(dv.getNextHop().equals(ip) && dv.getNextPort() == port)){
								dv.setInfinite();
								node.set(i, dv);
							}
						}
						StringBuffer buff = new StringBuffer();
						buff.append("LINKDOWN").append(";").append(localIP).append(";").append(listenPort).append("\n");
						
						byte[] send = new byte[1000];
						send = buff.toString().getBytes();
						DatagramPacket tempPacket = new DatagramPacket(send, send.length, InetAddress.getByName(ip), port);
						socket.send(tempPacket);
						
					}
					else{
						System.out.println("invalid destination");
					}
				}
				
				else if(command.equals("LINKUP")){
					if(split.length != 3){
						System.out.println("wrong command");
						return;
					}
					String ip = split[1];
					int port = Integer.parseInt(split[2]);
					for(int r = 0; r < dList.size(); r ++){
						if(dList.get(r).ip.equals(ip) && dList.get(r).port == port)
							dList.remove(r);
					}
					
					//System.out.println("dList size " + dList.size());
					//for(int i = 0; i < dList.size(); i ++){
						//System.out.println("dList element " + dList.get(i).port);
					//}
					
					System.out.println("dList removed");
					int index;
					int nIndex;
					if((index = searchNode(ip,port)) != -1){
						NodeTimer nt = null;
						nt = nodeAddress.get(index);
						
						nt.setConnect();
						nodeAddress.set(index,nt);
						
						DistanceVector dv = null;
						int idx;
						
						for(int i = 0; i < node.size(); i ++){
							dv = node.get(i);
							if((dv.getIP().equals(ip) && dv.getPort() == port)||(dv.getNextHop().equals(ip) && dv.getNextPort() == port)){
								dv.setFinite();
								node.set(i, dv);
							}
						}
						StringBuffer buff = new StringBuffer();
						buff.append("LINKUP").append(";").append(localIP).append(";").append(listenPort).append("\n");
						
						byte[] send = new byte[1000];
						send = buff.toString().getBytes();
						DatagramPacket tempPacket = new DatagramPacket(send, send.length, InetAddress.getByName(ip), port);
						socket.send(tempPacket);
					}
					
				}
				else if(command.equals("SHOWRT")){
					StringBuffer buffer =  new StringBuffer();
					Calendar cal = Calendar.getInstance();
					SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd EEE HH:mm:ss");
					cal.setTimeInMillis(System.currentTimeMillis());
					buffer.append(sdf.format(cal.getTime())).append("\n");
					
					for(int i = 1; i < node.size(); i ++){
						
						buffer.append("Dest  " + node.get(i).getIP() + " " + node.get(i).getPort() + " ");
						buffer.append("Cost  " + node.get(i).getCost() + " ").append("Link  " + node.get(i).getNextHop() + "  ");
						buffer.append(node.get(i).getNextPort()).append("\n");
					}

		            ShowTable showTable = new ShowTable();
		            showTable.setRouting(buffer.toString());
					//System.out.println(buffer.toString()); 
		            showTable.createAndShowUI();

				}
				else if(command.equals("CLOSE")){
					//socket.close();
					notClose = false;
					sendTimer.cancel();
				}
			}
			
			public void run(){
				BufferedReader stdIn = null;
				stdIn = new BufferedReader(new InputStreamReader(System.in));
				
				try{
					//System.out.println("input thread started");
					//out = new PrintWriter(socket.getOutputStream(), true); 
					while(true){
					    String fromUser;
					    if((fromUser = stdIn.readLine()) != null){
					    	
					    	processCommand(fromUser);
					    }
					   }
				    	
				    	
				}catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
		
		class InitNeighbor{
			private DatagramSocket socket = null;
			//private int neighborCount;
			private String input[] = null;
			private NodeTimer ipPort = null;
			
			public InitNeighbor(String input[], DatagramSocket socket) throws UnknownHostException{
				//this.neighborCount = neighborCount;
				this.socket = socket;
				this.input = input;
				
			}
			
			private void initDistanceVector() throws IOException{
				int portNr;
				double tempCost;
				String tempIP;
				DistanceVector tempVector;
				int argsSize = neighborNr;
			    int i = 0;
				while(argsSize -- > 0){
					
					//System.out.println("argsSize " + argsSize);
					//System.out.println("adding node" + argsSize);
					tempIP = input[i + 3];
					portNr = Integer.parseInt(input[i + 4]);
					tempCost = Double.parseDouble(input[i + 5]);
				
					i = i + 3;
					//System.out.println(tempIP + " " + portNr + " " + tempCost + "i " + i + "argsSize" + argsSize + "\n");
					tempVector = new DistanceVector(tempIP, portNr, tempCost);
					
				
					node.add(tempVector);
					DistanceVector test = node.get(node.size() - 1);
					//System.out.println(test.getIP() + test.getPort() + test.getCost() + test.getNextHop() + test.getNextPort());
					ipPort = new NodeTimer(tempIP, portNr,cIndex,tempCost);
					
					nodeAddress.add(ipPort);
					cIndex ++;
					ArrayList<DistanceVector> newNeighbor = new ArrayList<DistanceVector>();
					newNeighbor.add(new DistanceVector(tempIP, portNr, 0));
					neighbor.add(newNeighbor);
				}
				
				
				sendToAll(socket);
				
			}
		}
		
		public void sendToAll(DatagramSocket socket) throws IOException{
			StringBuffer buffer = new StringBuffer();
			DistanceVector dv = null;
			NodeTimer ipPort = null;
			DatagramPacket tempPacket = null;
			byte[] sendData = new byte[1000];
			//buffer.append(localVector.getIP()).append(";").append(localVector.getPort()).append(";");
			
			for(int idx = 0; idx < node.size(); idx ++){
				dv = node.get(idx);
				
				buffer.append(dv.getIP()).append(";").append(dv.getPort()).append(";").append(dv.getCost()).append(";");
				buffer.append(dv.getNextHop()).append(";").append(dv.getNextPort()).append("\n");
			}
			//System.out.println("sendToAll " + buffer.toString());
			sendData = buffer.toString().getBytes();
			//System.out.println(sendData.toString());
			
			int neighborCount;
			neighborCount = nodeAddress.size();
			for(int k = 0; k < neighborCount; k ++){
				ipPort = nodeAddress.get(k);
				if(nodeAddress.get(k).broken)
					continue;
				
				tempPacket = new DatagramPacket(sendData, sendData.length,InetAddress.getByName(ipPort.getIP()),ipPort.getPort());
				//linkCostUpdate();
				socket.send(tempPacket);
				
				//System.out.println("send To all " + buffer.toString());
			}
			sendTimer = new Timer();
			ResendTimeTask resendTimeTask = new ResendTimeTask(socket);
			sendTimer.schedule(resendTimeTask, timeOut, timeOut);
		}
		
		
		class NodeTimer{
			private String ip;
			private int port;
			private Timer timer = null;
			private double linkCost;
			private boolean broken = false;
			private int index;
			private boolean hasTimer = false;
			private CheckBreak checkbreak = null;
			private double subcost;
			
			NodeTimer(String nodeIP, int nodePort, int neighborIndex, double cost){
				this.ip = nodeIP;
				this.port = nodePort;
				this.index = neighborIndex;
				this.linkCost = cost;
				this.subcost = cost;
				this.timer = new Timer();
				//checkbreak = new CheckBreak(index);
				broken = false;
				if(!hasTimer){
					//System.out.println("Timer set up for " + ip + port + "\n");
					checkbreak = new CheckBreak(index);
					timer.schedule(checkbreak, 3*timeOut, 3*timeOut);
					hasTimer = true;
					}
			}
			
			public String getIP(){
				return ip;
			}
			
			public int getPort(){
				return port;
			}
			
			
			public boolean exist(String tIP, int tPort){
				if(tIP.equals(ip) && tPort == port)
					return true;
				return false;
			}
			
			public boolean isBroken(){
				return broken;
			}
			
			public void setBroken(){
				broken = true;
				this.subcost = this.linkCost;
				this.linkCost = INF;
				timer.cancel();
				hasTimer = false;
			}
			
			public void setConnect(){
				broken = false;
				this.linkCost = this.subcost;
				if(!hasTimer){
					timer = new Timer();
					checkbreak = new CheckBreak(index);
					timer.schedule(checkbreak, 3*timeOut, 3*timeOut);
					hasTimer = true;
				}
				else{
					checkbreak.justReceived();
				}
			}
			
			public void setStop(){
				broken = true;
				timer.cancel();
			}
			
			public double getCost(){
				return linkCost;
			}
		}
}

