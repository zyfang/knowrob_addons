/*
 * Copyright (c) 2010 Nacer Khalil
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Technische Universiteit Eindhoven nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
*/

package edu.tum.cs.ias.knowrob.comp_barcoo;

import ros.*;
import ros.pkg.vision_msgs.msg.aposteriori_position;
import ros.pkg.vision_msgs.msg.cop_answer;
import ros.pkg.vision_msgs.msg.cop_descriptor;
import ros.pkg.vision_srvs.srv.cop_call;
import ros.pkg.vision_srvs.srv.srvjlo;
import ros.pkg.vision_srvs.srv.srvjlo.Response;
import ros.pkg.vision_srvs.srv.srvjlo.Request;
import ros.pkg.vision_msgs.msg.partial_lo;

import java.util.concurrent.ExecutionException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Queue;
import java.util.Vector;
import java.lang.Exception;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.lang.Runnable;

import jpl.Query;

public class BarcooROSclient {
	
	static Boolean rosInitialized = false;
	static Ros ros;
	static NodeHandle n;

	
	static Subscriber.QueueingCallback<cop_answer> copObjectDetectionsCallback;
	static Subscriber.QueueingCallback<cop_answer> copModelDBCallback;

	Thread listenToCopDB;
	Thread updateKnowRobModelDB;
	Thread listenToCopObjDetections;
	Thread updateKnowRobObjDetections;
	
	public BarcooROSclient(String node_name) throws InterruptedException, RosException, ExecutionException
	{
		initRos(node_name);
		
		copObjectDetectionsCallback = new Subscriber.QueueingCallback<cop_answer>();
		copModelDBCallback = new Subscriber.QueueingCallback<cop_answer>();
	}
	
	public void startCopModelDBListener(String cop_topic, String cop_service) 
	{
		
		// create threads listening to /knowrob/cop_db to update the CoP database content
		listenToCopDB = new Thread( new CopModelDBListenerThread(cop_topic, cop_service) ); 
		listenToCopDB.start();
		updateKnowRobModelDB = new Thread( new UpdateKnowRobModelDBThread() ); 
		updateKnowRobModelDB.start(); 		
		
	}
	
	public void startCopObjDetectionsListener(String cop_topic) 
	{

		// create threads listening to /kipla/cop_reply to receive object detections
		listenToCopObjDetections = new Thread( new CopObjListenerThread(cop_topic) ); 
		listenToCopObjDetections.start();
		updateKnowRobObjDetections = new Thread( new UpdateKnowRobObjectsThread() ); 
		updateKnowRobObjDetections.start(); 
	}
	
	/**
	 * Thread-safe ROS initialization
	 */
	protected static void initRos(String node_name) 
	{

    	ros = Ros.getInstance();

		if(!Ros.getInstance().isInitialized()) {
	    	ros.init(node_name);
		}
		n = ros.createNodeHandle();

	}
	
	public static class CopObjListenerThread implements Runnable {
    	
    	String topic;
    	
    	public CopObjListenerThread() {
			this("/kipla/cop_reply");
		}
    	
    	public CopObjListenerThread(String t) {
			topic=t;
		}
    	
    	public void run() {
    		
    		try {

	    		Subscriber<cop_answer> sub = n.subscribe(topic, new cop_answer(), copObjectDetectionsCallback, 10);
	    		
	    		n.spin();
	    		sub.shutdown();
	    		
    		} catch(RosException e) {
    			e.printStackTrace();
    		}
    	}
    }
	
public static class UpdateKnowRobObjectsThread implements Runnable {
    	
    	@Override public void run() {
    		
    		try {
    			
    			cop_answer res;
    			HashMap<String, Vector<Object>> solutions;
    			while(true) {
    				
    				res = copObjectDetectionsCallback.pop();

    				System.err.println("UpdateKnowRobObjectsThread: received data");
    				
        			// iterate over detected poses
        			for(aposteriori_position pose : res.found_poses) {
        				
        				long   p_id    = pose.objectId;
        				//double p_prob  = pose.probability;
        				long   p_lo_id = pose.position;
        				HashMap<String, String> m_types_classes = new HashMap<String, String>();
        				
        				
        				// iterate over models used for detecting these poses
        				for(cop_descriptor model : pose.models) {
        					
        					//long   m_id    = model.object_id;
        					//double m_qual  = model.quality;
        					m_types_classes.put(model.type, model.sem_class);       					
        					
        				}

        				// create VisualPerception instance
        				//System.err.println("comp_cop:cop_create_perception_instance("+objectArrayToPlList(m_types_classes.keySet().toArray())+", Perception)");
        				solutions = executeQuery("comp_cop:cop_create_perception_instance("+objectArrayToPlList(m_types_classes.keySet().toArray())+", Perception)");
        				if(solutions.get("Perception").size()>1) {throw new Exception("ERROR: More than one Perception instance created.");}
        	    		String perception = solutions.get("Perception").get(0).toString();
        	    		
        	    		
        				// create object information
        	    	    System.err.println("comp_barcoo:cop_create_object_instance("+objectArrayToPlList(m_types_classes.values().toArray())+", '-"+p_id+ "', Obj)");
        				solutions = executeQuery("comp_barcoo:cop_create_object_instance("+objectArrayToPlList(m_types_classes.values().toArray()) + ", '-"+p_id+ "', Obj)");
        				if(solutions.get("Obj").size()>1) {throw new Exception("ERROR: More than one Object instance created:"+objectArrayToPlList(solutions.get("Obj").toArray()));}
        				String obj = solutions.get("Obj").get(0).toString();


        				// read pose from loID, assert it (remember lo-ID)
        	    		//System.out.println();
        				if(p_lo_id!=0) {
        					synchronized(jpl.Query.class) {
								new Query("comp_cop:cop_set_loid("+perception+", "+p_lo_id+")").allSolutions();
								partial_lo lo_pose = loQuery("framequery", "", (int)p_lo_id, 1);
								new Query("comp_cop:cop_set_perception_pose("+perception+","+doubleArrayToPlList(lo_pose.pose)+")").allSolutions();
								new Query("comp_cop:cop_set_perception_cov("+ perception+","+doubleArrayToPlList(lo_pose.cov) +")").allSolutions();								
        					}
        				}
						
        				synchronized(jpl.Query.class) {
							// link VisualPerception instance to the object instance
							new Query("comp_cop:cop_set_object_perception("+obj+", "+perception+")").allSolutions();
        				}
        			}
    				n.spinOnce();
 
    			}
    			
    		} catch(Exception e) {
    			e.printStackTrace();
    		}
    	}
    }
	
public static class CopModelDBListenerThread implements Runnable {
	
	String topic;
	String service;
	
	public CopModelDBListenerThread() {
		this("/knowrob/cop_db", "/cop/in");
	}
	
	public CopModelDBListenerThread(String t, String s) {
		topic=t;
		service=s;
	}	
	@Override public void run() {
		
		try {
			
    		Subscriber<cop_answer> sub = n.subscribe(topic, new cop_answer(), copModelDBCallback, 10);

    		// call cop to get the cop_descriptor model
    		cop_call copcall = new cop_call();
    		cop_call.Request cop_req = copcall.createRequest();
    		
    		cop_req.outputtopic="/knowrob/cop_db";
    		cop_req.action_type=25601;
    		cop_req.number_of_objects=1;
    		
//not required any more with new rosjava: auto-initialized
//    		cop_req.list_of_poses=new apriori_position[1];
//    		cop_req.list_of_poses[0] = new apriori_position();
//    		cop_req.list_of_poses[0].positionId=1;
//    		cop_req.list_of_poses[0].probability=1.0;
    		
    		cop_req.object_ids = new long[1];
    		cop_req.object_ids[0] = 1;
    		
    		ServiceClient<cop_call.Request, cop_call.Response, cop_call> cl = n.serviceClient(service, new cop_call());
    		cl.call(cop_req);	
    		
    		n.spin();
    		sub.shutdown();
    		
		} catch(RosException e) {
			e.printStackTrace();
		}
	}
}

    /**
     * Thread for updating the representation of the CoP model DB in KnowRob
     * 
     * @author tenorth@cs.tum.edu
     *
     */

    public static class UpdateKnowRobModelDBThread implements Runnable {
    	
    	@Override public void run() {
    		
    		try {
    			
    			cop_answer res;
    			while(true) {
    				
    				res = copModelDBCallback.pop();

    				System.err.println("GOT RESULT IN KNOWROB UPDATE");

        			// iterate over objects
        			for(aposteriori_position pose : res.found_poses) {
        				
        				// iterate over model associated to these objects
        				for(cop_descriptor model : pose.models) {

        					// create VisualPerception instance
            				System.out.println("comp_cop:cop_create_model_instance('"+model.type+"', "+model.sem_class+")");
            				executeQuery("comp_cop:cop_create_model_instance('"+model.type+"', "+model.sem_class+")");
            					
        				}

        				
        			}
    				n.spinOnce();
 
    			}
    			
    		} catch(Exception e) {
    			e.printStackTrace();
    		}
    	}
    }

	
	/**
	 * Convert a Java array into a Prolog list to be used in the string-based query interface
	 * 
	 * @param a The array to be converted
	 * @return A Prolog list of the form ['a0', 'a1']
	 */
	protected static String objectArrayToPlList(Object[] a) {
		String res="[";
		for(int i=0;i<a.length;i++) {
			
			if(a[i].toString().startsWith("'"))
				res+=a[i].toString();
			else 
				res+="'"+a[i].toString()+"'";
				
			if(i<a.length-1)
				res+=",";
		}
		return res+="]";
	}
	
	protected static String doubleArrayToPlList(double[] a) {
		String res="[";
		for(int i=0;i<a.length;i++) {
			res+="'"+a[i]+"'";
			if(i<a.length-1)
				res+=",";
		}
		return res+="]";
	}
	
	
	/**
	 * 
	 * Translate a CoP identifier into the corresponding KnowRob string. Uses the
	 * mapping defined in comp_barcoo.pl
	 * 
	 * @param copIdentifier The name of something in CoP
	 * @return              The name of the same thing in KnowRob
	 */
	private static String copToKnowrob(String copIdentifier) {
		
		HashMap<String, Vector<Object>> solutions = 
			executeQuery("downcase_atom('"+copIdentifier+"', CopID),cop_to_knowrob(CopID, KnowRobID)");
		
		if(solutions !=null) {
			Vector<Object> mappings = solutions.get("KnowRobID");
			
			if(mappings!=null && mappings.size()>0) {
				return mappings.get(0).toString();
			}
		}
		return "";
	}
	
	
	/**
	 * 
	 * Interface to the jlo service for coordinate transformations. Send a query
	 * to jlo to retrieve the transformation for a point in a given coordinate frame.
	 * 
	 * @param command   jlo query type: 'namequery' or 'framequery'
	 * @param objName   if namequery: the name of the object to query for
	 * @param objID     if framequery: the loID of the object to query for
	 * @param refFrame  if framequery: the loID of the desired parent frame the result should be returned in
	 * @return          partial_lo with the coordinates of the desired lo object in the frame given by refFrame
	 * @throws RosException
	 */
	public static partial_lo loQuery(String command, String objName, int objID, int refFrame) throws RosException {
		
	 	ServiceClient<Request, Response, srvjlo> client = n.serviceClient("/located_object", new ros.pkg.vision_srvs.srv.srvjlo());
	
	 	srvjlo srv = new srvjlo();
	 	Request rq = srv.createRequest();
	 	
	 	rq.command=command;
	 	
	 	if(command.equals("namequery")) {
	 		rq.query.name=objName;
	 	}
	 	else if(command.equals("framequery")) {
	 		rq.query.id=objID;
	 		
	 		if(refFrame>0)
	 			rq.query.parent_id=refFrame;
	 	}
	 	
	 	return client.call(rq).answer;
	}
	
	/**
	 * Wrapper around the JPL Prolog interface
	 * 
	 * @param query A query string in common SWI Prolog syntax
	 * @return A HashMap<VariableName, ResultsVector>
	 */
	public static HashMap<String, Vector<Object>> executeQuery(String query) {
		
		HashMap<String, Vector<Object>> result = new HashMap< String, Vector<Object> >();
		Hashtable[] solutions;

		synchronized(jpl.Query.class) {
		
    		Query q = new Query( "expand_goal(("+query+"),_9), call(_9)" );

    		if(!q.hasSolution())
    			return new HashMap<String, Vector<Object>>();
    		
    		
    		solutions = q.allSolutions();
    		for (Object key: solutions[0].keySet()) {
    			result.put(key.toString(), new Vector<Object>());
    		}
    		
    		// Build the result
    		for (int i=0; i<solutions.length; i++) {
    			Hashtable solution = solutions[i];
    			for (Object key: solution.keySet()) {
    				String keyStr = key.toString();
    				if (!result.containsKey( keyStr )) {

    					// previously unknown column, add result vector
    					Vector<Object> resultVector = new Vector<Object>(); 
    					resultVector.add( i, solution.get( key ).toString() );
    					result.put(keyStr, resultVector);

    				}
    				// Put the solution into the correct vector
    				Vector<Object> resultVector = result.get( keyStr );
    				resultVector.add( i, solution.get( key ).toString() );
    			}
    		}
		}
		// Generate the final QueryResult and return
		return result;
	}
	


/**
 * encapsulate the CoP interaction (triggering an action by calling a
 * service and listening on a topic for the results
 * @param id The CoP ID to be queried for
 * @return Array{modelType, semClass} for this CoP ID
 */

public static String[] copModelTypeSemClassForID(long id) {

	// call cop to get the cop_descriptor model
	cop_call copcall = new cop_call();
	cop_call.Request cop_req = copcall.createRequest();
	
	cop_req.outputtopic="/knowrob/cop_client";
	cop_req.action_type=25600;
	cop_req.number_of_objects=1;
	
//  not required any more with new rosjava: auto-initialized
//	cop_req.list_of_poses=new apriori_position[1];
//	cop_req.list_of_poses[0] = new apriori_position();
//	cop_req.list_of_poses[0].positionId=1;
//	cop_req.list_of_poses[0].probability=1.0;
	
	cop_req.object_ids = new long[1];
	cop_req.object_ids[0] = id;
	
	cop_answer r = copOneResult(cop_req, "/knowrob/cop_client");
	
	String[] res = null;
	if(r!=null) {
		String modelType=""; String objClass="";
		for(aposteriori_position pose : r.found_poses) {
			
			// use first entry as model class
			if(pose.models.size() > 0) {
				modelType=pose.models.get(0).type;
				objClass=pose.models.get(0).sem_class;
			}
		}
		res = new String[2];
		res[0]=modelType;
		res[1]=objClass;
	}
	return res;
}



/**
 * call CoP and return the first result published on the given response topic
 * @param cop_req
 * @param output_topic
 * @return
 */
public static cop_answer copOneResult(cop_call.Request cop_req, String output_topic) {  	

	initRos("knowrob_cop_one_result");
	
	// call the cop service and subscribe to the answer topic
	ServiceClient<cop_call.Request, cop_call.Response, cop_call> cl = n.serviceClient("/cop/in", new cop_call());
	cop_answer r=null;
	
	try {
		
		// create a queuing callback that is used for listening on the answer topic
		Subscriber.QueueingCallback<cop_answer> cop_callback = new Subscriber.QueueingCallback<cop_answer>();
		Subscriber<cop_answer> sub = n.subscribe(output_topic, new cop_answer(), cop_callback, 10);
		
		
		// make sure the queue is empty and wait one iteration to have everything setup up 
		cop_callback.clear();
		ros.spinOnce();
		
		// call CoP and wait until a result is received
		cl.call(cop_req);			
		while (cop_callback.isEmpty()) {
			ros.spinOnce();
		}

		// just read the first result returned on the output_topic
		r = cop_callback.pop();
		sub.shutdown();
		
	} catch (RosException e) {
		ros.logError("CopROSClient: Call to service /cop/in failed");
	} catch (InterruptedException e) {
		e.printStackTrace();
	}

	cl.shutdown();
	return r;
}


}