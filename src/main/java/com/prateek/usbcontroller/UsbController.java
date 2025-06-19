/**
 * UsbController - A utility class to enable or disable USB devices based on serial number.
 * <p>
 * This tool uses sysfs port control under /sys/bus/usb/devices/{usbid}/port/disable
 * to force USB port power down or restoration.
 *
 * Usage:
 *   sudo java -jar UsbController.jar -s <serial> -a [on|off]
 *
 * Dependencies:
 *   - Requires adb installed for device detection
 *   - Must run with sudo privileges for sysfs modification
 *
 * Author: Prateek Das prtkds@gmail.com
 */

package com.prateek.usbcontroller;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class UsbController {
	private static boolean enable = false, disable = false;
	private static String serial = "";
	private static List<String> olist;

	public static void main(String[] args) {
		parseArgs(args);
		run(serial, enable, disable);
	}
	
	/**
	 * Main logic to enable or disable USB device based on serial number.
	 * 
	 * @param serial  USB device serial number
	 * @param enable  true to enable the device, false to disable
	 * @param disable true to disable the device, false to enable
	 */
	public static void run(String serial, boolean enable, boolean disable) {
		String usbRealPath = "";
		File statusFile = new File(serial+".disabled");
		if(enable) {
			System.out.println("Enabling the device..");
			
			try {
				if(statusFile.exists()) {
					usbRealPath = Files.readAllLines(statusFile.toPath())
					.get(0);
					
					enableDevice(usbRealPath);
					Files.deleteIfExists(statusFile.toPath());
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			System.exit(0);
		}
		
		String bus = null, device = null;
		
		olist = runCommand("adb devices -l");
		for(String str: olist) {
			System.out.println(str);
		}
		String usbid = olist.stream()
				.filter(x->x.contains(serial))
				.map(x->x.substring(x.indexOf("usb:")+4, x.indexOf("product")))
				.collect(Collectors.toList()).get(0);
		System.out.println("UsbId: "+usbid);
		
		olist.clear();
		
		olist= runCommand("lsusb -v|grep -Ei \"Bus .*Device |iserial\"");
		
		String strTemp = null;
		for(String str: olist) {
			if(str.contains("Bus") && str.contains("Device")) {
				strTemp = str;
			}
			if(str.contains("iSerial") && str.contains(serial)) {
				if(strTemp!=null) {
					String[] strSp = strTemp.split(":")[0].split("\\s");
					bus = strSp[1];
					device = strSp[3];
				}
				break;
			}
		}
		
		olist.clear();
		
		if(bus!=null && device!=null) {
			System.out.println("Bus: "+bus+" Device: "+device);
		}
		
		olist.clear();
		
		Path usbSysPath = Paths.get("/sys/bus/usb/devices/",usbid.trim(),"port","disable");
		
		try {
			usbRealPath = usbSysPath.toRealPath().toString();
			System.out.println(usbSysPath + " "+usbRealPath);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		if(disable) {
			System.out.println("Disabling the device..");
			disableDevice(usbRealPath);
			try {
				FileWriter fw = new FileWriter(serial+".disabled");
				fw.write(usbRealPath);
				fw.flush();
				fw.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Parses command-line arguments:
	 * - `-s <serial>`: The USB serial number to target
	 * - `-a on|off`  : Action to perform: 'on' to enable, 'off' to disable
	 */
	private static void parseArgs(String[] args) {
		String tag = "";
		if(args.length ==0 ||  args[0].equalsIgnoreCase("-h")) {
			System.out.println("Run sudo java -jar UsbController.jar -s <serial number> -a [on|off]");
			System.exit(0);;
		}
		
		for(int i=0; i<args.length;i++) {
			if(args[i].startsWith("-")) {
				tag = args[i];
			}
			else {
				switch (tag) {
				case "-s":
					serial = args[i];
					break;
				case "-a":
					enable = args[i].equalsIgnoreCase("on");
					disable = args[i].equalsIgnoreCase("off");
					break;
				default:
					break;
				}
			}
		}
	}

	/**
	 * Enables the USB port by writing '0' to the resolved disable path.
	 *
	 * @param usbRealPath the full resolved path to the USB port's disable control file
	 */
	private static void enableDevice(String usbRealPath) {
		String cmd = "echo '0' | tee "+usbRealPath;
		List<String> olist = runCommand(cmd);
		olist.forEach(System.out::println);
		olist.clear();
	}

	/**
	 * Disables the USB port by writing '1' to the resolved disable path.
	 * Also persists the path in a .disabled marker file named after the serial.
	 *
	 * @param usbpath resolved sysfs path to disable port
	 */
	private static void disableDevice(String usbpath) {
		List<String> olist;

		String cmd = "echo '1' | tee " + usbpath;
		
		olist = runCommand(cmd);
		olist.forEach(System.out::println);
	}

	/**
	 * Executes a bash shell command and returns the output lines.
	 *
	 * @param cmd bash command string
	 * @return list of output lines
	 */
	public static List<String> runCommand(String cmd){
		List<String> returnStrL = new ArrayList<String>();
		System.out.println("Running command: "+cmd);
		ProcessBuilder pb = new ProcessBuilder();
		pb.command("bash", "-c", cmd);
		pb.redirectErrorStream(true);

		try {
			Process process = pb.start();
	        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
	            reader.lines().forEach(returnStrL::add);
	        }
	
	        process.waitFor();
	        //System.out.println("Process exited with code: " + exitCode);
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
		
		return returnStrL;
	}
}
