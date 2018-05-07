package representation;

import org.jfree.util.ArrayUtilities;

/**
 * Computes cosine similarity or vector difference for now.
 * @author MD
 *
 */
public class SimilarityCalculator {
	
	private static double dotProduct(int[] a, int[] b){
		double ans = 0;
		for(int i= 0 ; i < a.length; i++) //it assumes a and b are of the same length
			ans = ans + (a[i]*b[i]);
		return ans;
	}

	private static double magnitude(int[] a){
		double ans = 0;
		for(int i= 0 ; i < a.length; i++)
			ans = ans + (a[i] * a[i]);
		return Math.sqrt(ans);
	}
	
	/**
	 * Computes the cosine similarity between vectors a and b.
	 * @param a
	 * @param b
	 * @return a number between [-1,1] or -10 if vectors are of different size
	 */
	public static double cosineSimilarity(int[] a, int[] b){
		if(a.length != b.length)
			return -10;
		return dotProduct(a, b)/(magnitude(a)*magnitude(b));
	}
	
	/**
	 * Computes the difference between a and b and returns the resulting vector
	 * @param a
	 * @param b
	 * @return may return null if the two are of different sizes
	 */
	public static int[] vectorDifference(int[] a, int[] b){
		if(a.length != b.length)
			return null;
		int[] result = new int[a.length];
		for(int i= 0 ; i < a.length; i++){
			result[i] = a[i]-b[i];
		}
		return result;
		
	}
	
}
