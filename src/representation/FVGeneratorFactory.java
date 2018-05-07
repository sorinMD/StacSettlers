package representation;

public class FVGeneratorFactory {
	static final boolean BINARY_FEATURES = false;
	static boolean initialised = false;
	
	
	public static FVGenerator getGenerator(){
		if(!initialised)
			initialise();
			
		if(BINARY_FEATURES)
			return new BinaryFVGenerator();
		else
			return new NumericalFVGenerator();
	}
	
	public static void initialise(){
		if(BINARY_FEATURES)
			BinaryFVGenerator.initialiseGenerator();
		else
			NumericalFVGenerator.initialiseGenerator(NumericalFVGenerator.CURRENT_STATE_VECTOR_TYPE);
		initialised = true;
	}
	
}
