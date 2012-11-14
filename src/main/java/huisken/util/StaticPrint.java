package huisken.util;

import ij.IJ;
import ij.plugin.PlugIn;

public class StaticPrint implements PlugIn {

	static int i = 0;

	public void StaticPrint() {
		IJ.register(this.getClass());
	}

	public void run(String args) {
		i++;
		System.out.println("bla");
	}
}


