import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class ParS {
	// static String host = "www-itec.uni-klu.ac.at";
	// static String host = "localhost";
	//static String host = "172.17.20.167";
	//static String host = "192.168.1.2";
	static int segmentduration = 15;
	long playbacktime = 0;
	static long buffertime = 0;
	static long stallduration = 0;
	static long timetostartplayback = 0;
	static int numberofstalls = 0;
	static String host = "136.159.7.38";
	static long totalbitrate = 0;
	// static String path =
	// "/ftp/datasets/DASHDataset2014/BigBuckBunny/10sec/bunny_"; //46980bps/";
	// static String path = "/";
	static String path = "/www-itec.uni-klu.ac.at/ftp/datasets/DASHDataset2014/BigBuckBunny/15sec/bunny_";
	static String mpdpath = "/www-itec.uni-klu.ac.at/ftp/datasets/DASHDataset2014/BigBuckBunny/15sec/BigBuckBunny_15s_simple_2014_05_09.mpd";
	// http://www-itec.uni-klu.ac.at/ftp/datasets/DASHDataset2014/BigBuckBunny/10sec/bunny_45373bps/BigBuckBunny_2s2.m4s
	public static void main(String[] args) throws IOException {
		int connumber = Integer.parseInt(args[1]);// number of parallel connections
		
		System.out.println("number of parallel connections: " + connumber);

		// initializations
		int numsegments = 40; // number of segments Max: 596
		new File("./verynewresults/"+args[0]+"/").mkdirs();
		File log = new File("./verynewresults/"+args[0]+"/"+connumber + "_parallel connections_" + "bitrate-frequency.log");
		File log2 = new File("./verynewresults/"+args[0]+"/"+connumber + "_parallel connections_" + "results.log");
		PrintWriter writer = new PrintWriter(log, "UTF-8");
		PrintWriter writer2 = new PrintWriter(log2, "UTF-8");
		long bitrate = 0;
		// long totaldeliveredbytes = 0;

		// requesting segments
		for (int i = 1; i <= numsegments; i++) {
			long transmissionstart = System.currentTimeMillis(); // get time stamp before getting segment
																	 
			String filename = "BigBuckBunny_15s" + i + ".m4s";
			String adaptedrate = rateadapt(bitrate) + "";
			int segmentsize = getfilesize(adaptedrate, filename);
			// totaldeliveredbytes += segmentsize;
			int subsegmentsize = segmentsize / connumber;
			ArrayList<Thread> ths = new ArrayList<Thread>(connumber);
			for (int j = 0; j < connumber; j++) {
				int startrange = j * subsegmentsize;
				int endrange = (j + 1) * subsegmentsize;
				int port = 5200 + j;
				Thread th = new Thread() {
					public void run() {
						try {
							sendGet(startrange, endrange, filename, adaptedrate, port);
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				};
				ths.add(th);
				th.start();
				
			}
			for(int ii=0 ; ii< ths.size() ; ii++){
				try {
					ths.get(ii).join();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			// calculating transmission rate
			long transmissionend = System.currentTimeMillis(); // get time stamp after getting segment
															
			long transmissionduration = transmissionend - transmissionstart;
			if(i==1){									// first segment (playback hasn't started yet)
				timetostartplayback = transmissionduration;
				writer2.println("time to start playback(ms): " + timetostartplayback);
				writer.println("downloadRate	adaptedRate");	
			}
			else if(buffertime < transmissionduration){
				numberofstalls ++;
				stallduration += transmissionduration - buffertime;
				buffertime = 0;
			}
			buffertime += segmentduration * 1 * 1000;  //*1000 to make in milliseconds
			buffertime -= transmissionduration;
			bitrate = segmentsize / transmissionduration;
			bitrate *= 8000; // byte per milisec -> bit per sec

			// outputs
			System.out.println("Segment number: " + i);
			//System.err.println("Transmission Adapted Rate: " + adaptedrate + " bps");
			System.out.println("Transmission Rate: " +((float)bitrate/1000000) + " Mbps");
			System.out.println("buffer time: " + buffertime);
			// write log file
			writer.println(bitrate + "	" + adaptedrate);
			totalbitrate += bitrate;
		}
		writer2.println("average bitrate: " + (float)totalbitrate / (numsegments * 1000000) ); //Mbps
		writer2.println("Number of stalls:  " + numberofstalls);
		writer2.print("duration of stalls: " + stallduration);
		writer2.close();
		writer.close();
	}

	private static int getfilesize(String rate, String filename) throws IOException {
		Socket s;
		try {
			s = new Socket(InetAddress.getByName(host), 80);
			PrintWriter pw = new PrintWriter(s.getOutputStream());
			pw.println("HEAD " + path + rate + "bps/" + filename + " HTTP/1.1");
			pw.println("Host: " + host);
			pw.println("");
			pw.flush();
			BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
			String t;
			while ((t = br.readLine()) != null) {
				// System.out.println(t);
				String cl = "Content-Length: ";
				if (t.contains(cl)) {
					String filesize = t.substring(cl.length());
					int filesizebytes = Integer.parseInt(filesize);
					return filesizebytes;
				}
			}
			br.close();
			s.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return -1;
	}

	private static void sendGet(int startrange, int endrange, String filename, String rate, int portnumber)
			throws IOException {
		try {
			 HttpURLConnection s;
			 //save file
			 URL url = new URL("http://"+host+path+rate+"bps/"+filename);
			 s = (HttpURLConnection)url.openConnection();
			
			 //set other headers
			 s.setRequestProperty ("Range", "bytes="+startrange+"-"+endrange);
			
			 //connect
			 s.connect();

			BufferedInputStream in = new BufferedInputStream(s.getInputStream());

			OutputStream out = new BufferedOutputStream(new FileOutputStream(new File("./files/" + filename)));
			byte[] buf = new byte[256];
			int n = 0;
			while ((n = in.read(buf)) >= 0) {
				out.write(buf, 0, n);
				//System.out.println(buf);
			}
			out.flush();
			out.close();
//			s.close(); 
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static int rateadapt(long bitrate) {
		int [] rates = mpdrates();
		if (bitrate == 0)
			return rates[0];
		else {
			for (int i = 0; i < rates.length; i++) {
				if (bitrate == rates[i])
					return rates[i];
				else if (bitrate < rates[i])
					return i == 0 ? rates[0] : rates[i - 1];
				// Math.max(rates[0], rates[i-1]);
			}
		}
		return rates[rates.length - 1];
	}
	private static int[] mpdrates(){
		//downloading the MPD file
		try {			
			 HttpURLConnection s;
			 //save file
			 
			 URL url = new URL("http://"+host+mpdpath);
			 s = (HttpURLConnection)url.openConnection();
			
			 //connect
			 s.connect();

			BufferedInputStream in = new BufferedInputStream(s.getInputStream());

			OutputStream out = new BufferedOutputStream(new FileOutputStream(new File("./files/" + "rates.xml")));
			byte[] buf = new byte[256];
			int n = 0;
			while ((n = in.read(buf)) >= 0) {
				out.write(buf, 0, n);
				//System.out.println(buf);
			}
			out.flush();
			out.close();
//			s.close(); 
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		//parsing the xml file
		try{
			
			File fXmlFile = new File("./files/rates.xml");
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(fXmlFile);
			
			doc.getDocumentElement().normalize();
			NodeList nList = doc.getElementsByTagName("Representation");
			
			int [] rates = new int [nList.getLength()];
			for(int i=0 ; i<nList.getLength(); i++){
				Node nNode = nList.item(i);
				if (nNode.getNodeType() == Node.ELEMENT_NODE) {
					Element eElement = (Element) nNode;
					//System.out.println("Staff id : " + eElement.getAttribute("bandwidth"));
					rates[i] = Integer.parseInt(eElement.getAttribute("bandwidth"));
				}
			}
			return rates;
		} catch (Exception e){
			e.printStackTrace();
		}
		
		return null;
	}
}
