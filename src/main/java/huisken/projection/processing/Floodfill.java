package huisken.projection.processing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Stack;

public class Floodfill {

	public static final short SELECT = 0,
						SEGMENT = 1,
						DELETE = 2;
	private final IndexedTriangleMesh itm;
	private final int[] intensities;
	private final int segmentation[];
	private int segmentationCounter = 1;
	private final int[][] neighbors;
	private int selectedVertex = -1;
	private ArrayList<Integer> currentSegmentation = null;
	private int lowerThreshold;
	private int status = 0;
	private ArrayList<Integer> outline = null;
	private int selectedLabel;

	public Floodfill(IndexedTriangleMesh itm, int[] intensities, int[] segmentation) {
		this.itm = itm;
		this.intensities = intensities;
		this.segmentation = segmentation;
		for(int i = 0; i < segmentation.length; i++) {
			int v = segmentation[i] + 1;
			if(v > segmentationCounter)
				segmentationCounter = v;
		}
		neighbors = calculateNeighbors(this.itm.getFaces(), this.itm.getVertices().length);
	}

	public int[] getSegmentation() {
		return segmentation;
	}

	public ArrayList<Integer> getCurrentSegmentation() {
		return currentSegmentation;
	}

	public ArrayList<Integer> getOutline() {
		return outline;
	}

	public int getStatus() {
		return status;
	}

	public int getSelectedLabel() {
		return selectedLabel;
	}

	private void fill() {
		if(selectedVertex != -1)
		{
			LinkedList<Integer> selected = new LinkedList<Integer>();
			Stack<Integer> tmp = new Stack<Integer>();
			int vertex;
			boolean[] painted = new boolean[itm.getVertices().length];

			tmp.push(new Integer(selectedVertex));
			while (!tmp.isEmpty()) {
				vertex = tmp.pop();
				if (!painted[vertex] && intensities[vertex] >= lowerThreshold) {
					selected.push(vertex);
					painted[vertex] = true;
					for(int i=0; i<neighbors[vertex].length; ++i)
					{
						tmp.push(new Integer(neighbors[vertex][i]));
					}
				}
			}
			currentSegmentation = new ArrayList<Integer>(selected);

			HashSet<Integer> curr = new HashSet<Integer>(selected);
			outline = new ArrayList<Integer>();
			for(int v : currentSegmentation) {
				for(int i = 0; i < neighbors[v].length; i++) {
					if(!curr.contains(neighbors[v][i])) {
						outline.add(v);
						break;
					}
				}
			}
		}
	}

	public void calculateOutlines(boolean[] result) {
		if(segmentation == null) {
			Arrays.fill(result, false);
			return;
		}
		Arrays.fill(result, false);
		int[] faces = itm.getFaces();
		for(int i = 0; i < itm.nFaces; i += 3) {
			int f1 = faces[i];
			int f2 = faces[i + 1];
			int f3 = faces[i + 2];

			int s1 = segmentation[f1];
			int s2 = segmentation[f2];
			int s3 = segmentation[f3];

			if(s1 > 0 && (s2 == 0 || s3 == 0))
				result[f1] = true;
			if(s2 > 0 && (s1 == 0 || s3 == 0))
				result[f2] = true;
			if(s3 > 0 && (s1 == 0 || s2 == 0))
				result[f3] = true;
		}
	}

	public ArrayList<Integer> calculateOutlines(int label) {
		if(segmentation == null)
			return null;

		HashSet<Integer> outline = new HashSet<Integer>();

		int[] faces = itm.getFaces();
		for(int i = 0; i < itm.nFaces; i += 3) {
			int f1 = faces[i];
			int f2 = faces[i + 1];
			int f3 = faces[i + 2];

			int s1 = segmentation[f1];
			int s2 = segmentation[f2];
			int s3 = segmentation[f3];

			if(s1 == label && (s2 == 0 || s3 == 0))
				outline.add(f1);
			if(s2 == label && (s1 == 0 || s3 == 0))
				outline.add(f2);
			if(s3 == label && (s1 == 0 || s2 == 0))
				outline.add(f3);
		}
		return new ArrayList<Integer>(outline);
	}

	private int[][] calculateNeighbors(int[] faces, int nVertices) {
		HashSet<Integer>[] set = new HashSet[nVertices];
		for (int i = 0; i < nVertices; i++)
			set[i] = new HashSet<Integer>();

		for (int i = 0; i < faces.length; i += 3) {
			int f1 = faces[i];
			int f2 = faces[i + 1];
			int f3 = faces[i + 2];
			set[f1].add(f2);
			set[f1].add(f3);

			set[f2].add(f1);
			set[f2].add(f3);

			set[f3].add(f1);
			set[f3].add(f2);
		}

		int[][] neigh = new int[nVertices][];
		for (int i = 0; i < nVertices; i++) {
			neigh[i] = new int[set[i].size()];
			int n = 0;
			for (int neighbor : set[i])
				neigh[i][n++] = neighbor;
		}
		return neigh;
	}

	public void exec() {

		if(currentSegmentation != null && status == SEGMENT)
		{
			Iterator<Integer> it = currentSegmentation.iterator();
			while(it.hasNext())
			{
				segmentation[it.next()] = segmentationCounter;
			}
			++segmentationCounter;
			selectedVertex = -1;
			currentSegmentation = null;
			status = SELECT;
		}
		if(selectedVertex != -1 && status == DELETE)
		{
			int label = segmentation[selectedVertex];
			for(int i=0; i<segmentation.length; ++i)
			{
				if(segmentation[i] == label)
					segmentation[i] = 0;
			}
			status = SELECT;
		}
	}

	/**
	 * @return the lowerThreshold
	 */
	public int getLowerThreshold() {
		return lowerThreshold;
	}

	/**
	 * @param lowerThreshold
	 *            the lowerThreshold to set
	 */
	public void setLowerThreshold(int units) {
		if(status == SEGMENT) {
			lowerThreshold = Math.max(0, lowerThreshold + units);
			System.out.println("lower threshold = " + lowerThreshold);
			fill();
		}
	}

	/**
	 * @return the selectedVertex
	 */
	public int getSelectedVertex() {
		return selectedVertex;
	}

	/**
	 * @param selectedVertex the selectedVertex to set
	 */
	public void setSelectedVertex(int selectedVertex) {
		lowerThreshold = Math.max(0,intensities[selectedVertex]-15);
		this.selectedVertex = selectedVertex;
		if(segmentation[selectedVertex] == 0)
		{
			status = SEGMENT;
			selectedLabel = -1;
		}
		else
		{
			status = DELETE;
			selectedLabel = segmentation[selectedVertex];
		}
		if(status == SEGMENT)
			fill();
	}
}
