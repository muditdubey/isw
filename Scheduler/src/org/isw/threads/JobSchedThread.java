package org.isw.threads;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.PriorityQueue;
import java.util.Random;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.isw.FlagPacket;
import org.isw.Job;
import org.isw.MachineList;
import org.isw.Macros;
import org.isw.MaintenanceRequestPacket;
import org.isw.MaintenanceTuple;
import org.isw.Schedule;
import org.isw.SchedulingDept;

public class JobSchedThread extends Thread
{
	MachineList machineList;
	Random r = new Random();
	ServerSocket tcpSocket;
	ArrayList<Job> jobArray;
	int shiftCount;
	public JobSchedThread(MachineList machineList, int shiftCount)
	{
		this.machineList=machineList;
		this.shiftCount = shiftCount;
	}

	public void run()
	{	
		try 
		{
			tcpSocket = new ServerSocket(Macros.SCHEDULING_DEPT_PORT_TCP);
		} catch (SocketException e1) {
			e1.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		int cnt = 0;
		parseJobs(); // from Excel sheet
		
		while(cnt++ < shiftCount)
		{	
			// Priority Queue fetches schedules with least total job time
			PriorityQueue<Schedule> pq = new PriorityQueue<Schedule>();
			Enumeration<InetAddress> machineIPs = machineList.getIPs();
			while(machineIPs.hasMoreElements())
			{	
				InetAddress ip = machineIPs.nextElement();
				FlagPacket.sendTCP(Macros.REQUEST_PREVIOUS_SHIFT, ip, Macros.MACHINE_PORT_TCP);		
				System.out.println("Sent schedule get req to machines");
				Schedule jl = Schedule.receive(tcpSocket);
				jl.setAddress(ip);
				pq.add(jl);	// add pending job lists to priority queue
			}

			System.out.println("Job list: ");
			for(int i=0;i<jobArray.size();i++){
				/*
				 * Generate new schedule: Get schedule with minimum total time,
				 * and add the first job in jobArray to it.
				 */
				Schedule min = pq.remove(); 
				min.addJob(jobArray.get(i));
				System.out.print(jobArray.get(i).getJobName()+": "+String.valueOf(jobArray.get(i).getJobTime()/Macros.TIME_SCALE_FACTOR)+" ");
				pq.add(min);
			}
			
			
			Random rand = new Random();
			int count1 = 0;
			machineIPs = machineList.getIPs();
			while(machineIPs.hasMoreElements()){
				InetAddress ip = machineIPs.nextElement();
				Schedule sched = new Schedule();
				sched.setAddress(ip);
				for(int i=0;i<Macros.NO_OF_JOBS;i++){
					sched.addJob(jobArray.get(count1++));		
				}
				try {
					sched.send(sched.getAddress(), Macros.MACHINE_PORT_TCP);
					System.out.println("Sending schedule to "+sched.getAddress());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}
			/*
			while(!pq.isEmpty())
			{
				try {
					pq.peek().send(pq.peek().getAddress(), Macros.MACHINE_PORT_TCP);
					System.out.println("Sending schedule to "+pq.poll().getAddress());
				} catch (SocketException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}*/
			// schedules are sent
			// wait till all machines send REQUEST_NEXT_SHIFT
			FlagPacket fp;
			int count =0;
			while(count < machineList.count()){
				fp = FlagPacket.receiveTCP(tcpSocket,0);
				if(fp.flag == Macros.REQUEST_NEXT_SHIFT) // shift is over
					count++;
			}
			
			MaintenanceRequestPacket mrp = new MaintenanceRequestPacket(SchedulingDept.maintenanceIP, Macros.MAINTENANCE_DEPT_PORT_TCP, new MaintenanceTuple(-1));
			mrp.sendTCP();
				
		}
		
		// Simulation Complete
		System.out.println("Process Complete");
		Enumeration<InetAddress> en = machineList.getIPs();
		while(en.hasMoreElements())
		{
			FlagPacket.sendTCP(Macros.PROCESS_COMPLETE, en.nextElement(), Macros.MACHINE_PORT_TCP);
		}

	}
	private void parseJobs() 
	{
		/*
		 * Get jobs from Excel sheet
		 */
		jobArray = new ArrayList<Job>();
		try
		{
			FileInputStream file ;
			if(Macros.NO_OF_JOBS == 7)
				file = new FileInputStream(new File("Jobs.xlsx"));
			else 
				file = new FileInputStream(new File("Jobs_3.xlsx"));
			XSSFWorkbook workbook = new XSSFWorkbook(file);
			XSSFSheet sheet = workbook.getSheetAt(0);
			
			for(int i=1;i<=machineList.count()*Macros.NO_OF_JOBS;i++)
			{
				Row row = sheet.getRow(i);
				String jobName = row.getCell(0).getStringCellValue();
				long jobTime = (long)(row.getCell(1).getNumericCellValue()*Macros.TIME_SCALE_FACTOR);
				double jobCost = row.getCell(3).getNumericCellValue();	
				Job job = new Job(jobName,jobTime,jobCost,Job.JOB_NORMAL);
				job.setPenaltyCost(row.getCell(4).getNumericCellValue());
				jobArray.add(job);
			}
			file.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}	
		//sort jobs in descending order of job time
		//Collections.sort(jobArray, new JobComparator());
	}
}

class JobComparator implements Comparator<Job> {
	/*
	 * Sort jobs in descending order of job time
	 */
	@Override
	public int compare(Job a, Job b) 
	{
		return Double.compare(b.getJobTime(),a.getJobTime());
	}

	
}
