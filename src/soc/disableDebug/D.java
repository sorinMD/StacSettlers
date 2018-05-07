/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2007-2009 Jeremy D. Monin <jeremy@nand.net>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * The author of this program can be reached at thomas@infolab.northwestern.edu
 **/
package soc.disableDebug;

/**
 * Debug output; the disabled class is always off.
 * {@link soc.debug.D} and soc.disableDebug.D have the same interface, to easily switch
 * debug on and off per class.
 * 
 * Extended with 4 levels of importance: {@link #INFO}, {@link #WARNING}, {@link #ERROR}, {@link #FATAL};
 * Depending on the level, call one of the debug methods to print out.
 */
public class D
{
	/**
	 * Print out everything
	 */
	public static final int INFO = 0;
	/**
	 * Print out warnings or above
	 */
	public static final int WARNING = 1;
	/**
	 * Print out errors or fatals
	 */
	public static final int ERROR = 2;
	/**
	 * Print out fatals only. NOTE: despite the name, fatals are exceptions that may or may not cause the application to crash
	 */
	public static final int FATAL = 3;
	
    static public final boolean ebugOn = false;
    // static private boolean enabled = false;
    
	/**
	 * The debug level one of: {@link #INFO}, {@link #WARNING}, {@link #ERROR}, {@link #FATAL}
	 * Default set to WARNING.
	 * Doesn't affect anything as debug is off
	 */
	static private int level = WARNING;
    
	/**
	 * Changes the debug level to one of: {@link #INFO}, {@link #WARNING}, {@link #ERROR}, {@link #FATAL}
	 * The default is WARNING.
	 */
    public static void setLevel(int l){
    	level = l;
    }
    
    public static int ebug_level(){
    	return level;
    }

    /**
     * Does nothing, since this is the disabled version.
     */
    public static final void ebug_enable() {}

    /**
     * Always disabled; does nothing, since this is the disabled version.
     */
    public static final void ebug_disable() {}

    /**
     * Always returns false as debug is off
     */
    public static final boolean ebugIsEnabled()
    {
        return false;
    }

   /**
     * Does nothing as debug is off
     *
     * @param text DOCUMENT ME!
     */
    public static final void ebugPrintlnINFO(String text) {}
    
    /**
     * Does nothing as debug is off
     *
     * @param text DOCUMENT ME!
     */
    public static final void ebugPrintlnINFO(String prefix, String text) {}

    /**
     * Does nothing as debug is off
     */
    public static final void ebugPrintlnINFO() {}

    /**
     * Does nothing as debug is off
     * @param ex Exception or other Throwable
     * @param prefixMsg Message for {@link #ebugPrintlnINFO(String)} above the exception,
     *                  or null
     */
    public static final void ebugFATAL(Throwable ex, String prefixMsg) {}

    /**
     * DOCUMENT ME!
     *
     * @param text DOCUMENT ME!
     */
    public static final void ebugPrintINFO(String text) {}

//    /**
//     * Debug-println this text; for compatability with log4j.
//     * Calls {@link #ebugPrintln(String)}.
//     * @param text Text to debug-print
//     */
//    public static final void debug(String text) { ebugPrintln(text); }
    
    /**
     * Does nothing as debug is off
     * @param text Text to debug-print
     */
    public static final void ebugWARNING(String text) { }
    
    /**
     * Does nothing as debug is off
     * @param text Text to debug-print
     */
    public static final void ebugWARNING(String prefix, String text) { }
    
    /**
     * Does nothing as debug is off
     * @param text Text to debug-print
     */
    public static final void ebugERROR(String text) { }
    
    /**
     * Does nothing as debug is off
     * @param text Text to debug-print
     */
    public static final void ebugERROR(String prefix, String text) { }

}
