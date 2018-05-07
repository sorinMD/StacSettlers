package actr.env;

import java.io.*;
import java.net.URL;

import javax.swing.*;

/**
 * The class that defines <tt>main()</tt>, determines the operating system platform,
 * and opens the application.
 * 
 * @author Dario Salvucci
 */
public class Main
{
	/** The application's version string. */
	private static final String version = "1.1";

	/** The single core class used in the application. */
	public static Core core = null;

	/**
	 * Checks whether the application is running on the Macintosh platform.
	 * @return <tt>true</tt> if the system is running on the Macintosh platform, or <tt>false</tt> otherwise
	 */
	public static boolean onMac() { return System.getProperty("os.name").toLowerCase().contains("mac"); }

	/**
	 * Checks whether the application is running on the Windows platform.
	 * @return <tt>true</tt> if the system is running on the Windows platform, or <tt>false</tt> otherwise
	 */
	public static boolean onWin() { return System.getProperty("os.name").toLowerCase().contains("win"); }

	/**
	 * Checks whether the application is running on a *NIX platform.
	 * @return <tt>true</tt> if the system is running on a *NIX platform, or <tt>false</tt> otherwise
	 */
	public static boolean onNix()
	{
		return System.getProperty("os.name").toLowerCase().contains("nix")
		|| System.getProperty("os.name").toLowerCase().contains("nux");
	}

	/**
	 * Gets the version string, including main version number and revision number.
	 * @return the version string
	 */
	public static String getVersion ()
	{
		try
		{
			URL url = Main.class.getResource ("Version.txt");
			InputStream in = url.openStream ();
			StringWriter sw = new StringWriter ();
			int c;
			while ((c = in.read()) != -1) sw.write (c);
			in.close();
			return version + " r" + sw.toString();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return version;
		}
	}

	/**
	 * The main method called on startup.
	 * @param args the arguments
	 */
	public static void main (String[] args)
	{
		if (onMac()) MacOS.start();
		else
		{
			SwingUtilities.invokeLater (new Runnable() {
				public void run() {
					core = new Core ();
					core.startup();
				}
			});
		}
	}
}
