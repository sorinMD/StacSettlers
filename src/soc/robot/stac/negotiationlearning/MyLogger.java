package soc.robot.stac.negotiationlearning;

import java.util.logging.*;

/**
 * Wrapper around JAMES logger to accommodate printing formatted strings.
 *
 * @author Simon Keizer
 */
public class MyLogger {

    /** target logger */
    Logger logger;

    /** Constructor */
    public MyLogger( Logger log ) {
        logger = log;
    }
    
    public MyLogger( String name ) {
    	logger = Logger.getLogger( name );
    }
    
    public void addHandler(Handler handler) throws SecurityException {
    	logger.addHandler( handler );
    }
    
    public void setLevel( Level newLevel ) {
    	logger.setLevel( newLevel );
    }
    
    public void parseLevel( String lvl ) throws IllegalArgumentException {
    	Level newLevel = Level.parse( lvl );
    	logger.setLevel( newLevel );
    }
    
    public Level getLevel() {
    	return logger.getLevel();
    }

    /** Standard logging */
    public void log( Level lvl, String msg ) {
        logger.log( lvl, msg );
    }

    /** Logging of formatted strings */
    public void logf( Level lvl, String format, Object... args ) {
        String msg = String.format( format, args );
        logger.log( lvl, msg );
    }

	public void setUseParentHandlers(boolean b) {
		logger.setUseParentHandlers( b );
	}
    
}
