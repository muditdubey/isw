package org.isw.threads;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.PriorityQueue;
import java.util.Random;

import org.isw.FlagPacket;
import org.isw.Job;
import org.isw.MachineList;
import org.isw.Macros;
import org.isw.Schedule;

public class JobSchedThread extends Thread
{
	final static int SCHED_PUT = 3;
	final static int SCHED_GET = 4;
	MachineList machineList;
	Random r = new Random();
	DatagramSocket socket;
	ServerSocket tcpSocket;
	ArrayList<Job> jobArray; 
	public JobSchedThread(MachineList machineList)
	{
		this.machineList=machineList;
	}

	public void run()
	{	

		try {
			socket = new DatagramSocket(Macros.SCHEDULING_DEPT_PORT);
			tcpSocket = new ServerSocket(Macros.SCHEDULING_DEPT_PORT_TCP);
		} catch (SocketException e1) {
			e1.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		while(true)
		{	

			getJobs();
			PriorityQueue<Schedule> pq = new PriorityQueue<Schedule>();

			Enumeration<InetAddress> en = machineList.getIPs();
			while(en.hasMoreElements())
			{	
				InetAddress ip = en.nextElement();
				try {
					//request pending jobs from previous shift from machine
					final ByteArrayOutputStream baos=new ByteArrayOutputStream();
					final DataOutputStream daos=new DataOutputStream(baos);
					daos.writeInt(Macros.SCHEDULE_GET);
					daos.close();
					final byte[] bufOut=baos.toByteArray();
					DatagramPacket packetOut = new DatagramPacket(bufOut, bufOut.length, ip, Macros.MACHINE_PORT);
					socket.send(packetOut);

					byte[] bufIn = new byte[1024];
					DatagramPacket packet = new DatagramPacket(bufIn, bufIn.length);
					socket.receive(packet);
					byte[] object = packet.getData();
					ByteArrayInputStream in = new ByteArrayInputStream(object);
					ObjectInputStream is = new ObjectInputStream(in);
					Schedule jl = (Schedule) is.readObject();	
					jl.setAddress(ip);
					pq.add(jl);	
				} catch (IOException e) {
					e.printStackTrace();
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				} 

			}

			System.out.println("Job list: ");
			for(int i=0;i<jobArray.size();i++){
				Schedule min = pq.remove();
				min.addJob(jobArray.get(i));
				System.out.print(jobArray.get(i).getJobName()+": "+jobArray.get(i).getJobTime()/60+" ");

				pq.add(min);
			}

			while(!pq.isEmpty()){
				try {
					//send job list to machine
					ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
					ObjectOutputStream os = new ObjectOutputStream(outputStream);
					os.writeObject(pq.peek());
					byte[] object = outputStream.toByteArray();
					os.close();
					outputStream.reset();
					DataOutputStream ds = new DataOutputStream(outputStream);
					ds.writeInt(Macros.SCHEDULE_PUT);
					byte[] header =outputStream.toByteArray();
					ds.close();
					outputStream.reset();
					outputStream.write( header );
					outputStream.write( object );
					byte[] data = outputStream.toByteArray( );
					DatagramPacket sendPacket = new DatagramPacket(data, data.length, pq.poll().getAddress(), Macros.MACHINE_PORT);
					System.out.println("Sending schedule to "+pq.poll().getAddress());
					socket.send(sendPacket);
				} catch (SocketException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}

			FlagPacket fp;
			while(true){
				fp = FlagPacket.receiveTCP(tcpSocket,0);
				if(fp.flag == Macros.REQUEST_NEXT_SHIFT)
					break;
			}
		}
	}
	void getJobs(){
		jobArray = new ArrayList<Job>();
		for(int i=14;i>0;i--){ 
			if(r.nextBoolean()){
				Job job = new Job(String.valueOf(i),i*1200,i*1000,Job.JOB_NORMAL);
				job.setPenaltyCost(i*500);
				jobArray.add(job);
			}
		}
	}
}
