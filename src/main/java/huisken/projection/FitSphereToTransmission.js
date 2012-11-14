importPackage(Packages.huisken.projection);
importClass(Packages.javax.vecmath.Point3f);
importClass(Packages.java.util.Arrays);
importClass(Packages.java.util.Properties);
importClass(Packages.fiji.util.gui.GenericDialogPlus);
importPackage(java.io);
importPackage(java.net);

defaultdir = new File("D:\\SPIMdata");
tmp = defaultdir.listFiles();
if(tmp != null && tmp.length != 0) {
	Arrays.sort(tmp);
	defaultdir = tmp[tmp.length - 1];
}
tmp = defaultdir.listFiles();
if(tmp != null && tmp.length != 0) {
	Arrays.sort(tmp);
	defaultdir = tmp[tmp.length - 1];
}
dz = 0;
try{
	dz = LabView.readDouble("z spacing") * 1000;
	system.out.println("dz = " + dz);
} catch(err) {
	System.out.println(err);
}


gd = new GenericDialogPlus("");
gd.addDirectoryField("Directory", defaultdir.getAbsolutePath());
gd.addNumericField("z spacing", dz, 5);
gd.showDialog();


folder = new java.lang.String(gd.getNextString()).replaceAll("\\\\", "/") + '/';
System.out.println(folder);
pw = 0.65;
pd = gd.getNextNumber();

properties = new Properties();
properties.loadFromXML(new FileInputStream(folder + 'camera.xml'));
w = Integer.parseInt(properties.getProperty("AOIWidth"));
h = Integer.parseInt(properties.getProperty("AOIHeight"));
System.out.println(w + "x" + h);

command = 
	'run("Raw...", "open=' + folder + 'data/0000.dat image=[16-bit Unsigned] width=' + w + ' height=' + h + ' offset=0 number=1 gap=0 little-endian open")' +
	'setVoxelSize(' + pw + ', ' + pw + ', ' + pd + ', "um"); rename("original");' +
	'run("Scale...", "x=0.25 y=0.25 z=1.0 interpolation=Bicubic average process create title=image1");' +
	'selectWindow("original");' +
	'close();' +
	'run("Duplicate...", "title=image2 duplicate stack");' +
	'run("Gaussian Blur...", "sigma=1 stack");' +
	'imageCalculator("Difference stack", "image1","image2");' +
	'selectWindow("image2");' +
	'close();' +
	'waitForUser("Select a region and press ok");';
IJ.runMacro(command);


imp = IJ.getImage();
d = imp.getStackSize();
fs = IJ.runPlugIn("huisken.projection.Fit_Sphere", "");
System.out.println(fs);

center = new Point3f();
fs.getCenter(center);
System.out.println("***" + center.x);
radius = fs.getRadius();


props = new Properties();
props.setProperty("w", Integer.toString(w));
props.setProperty("h", Integer.toString(h));
props.setProperty("d", Integer.toString(d));
props.setProperty("pw", Double.toString(pw));
props.setProperty("ph", Double.toString(pw));
props.setProperty("pd", Double.toString(pd));
props.setProperty("centerx", Float.toString(center.x));
props.setProperty("centery", Float.toString(center.y));
props.setProperty("centerz", Float.toString(center.z));
props.setProperty("radius", Double.toString(radius));

props.storeToXML(new FileOutputStream(folder + "SMP.xml"), "SphericalMaximumProjection");

otherfolder = new java.lang.String(folder).replace("D:/SPIMdata", "U:");
file = new File(otherfolder);
if(!file.exists())
	file.mkdirs();
props.storeToXML(new FileOutputStream(otherfolder + "SMP.xml"), "SphericalMaximumProjection");
