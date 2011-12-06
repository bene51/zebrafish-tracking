folder = "/Users/bschmid/PostDoc/data/SPIMExampleData/110916_huisken/e001/s000/t00001/r000/a050/c001/z0000/plane_0000000000.dat";
w = 960;
h = 960;


run("Raw...", "open=" + folder + " image=[16-bit Unsigned] width=" + w + " height=" + h + " offset=0 number=1 gap=0 little-endian open");

pw = 1.14286;
pd = 3;
run("Gaussian Blur...", "sigma=1 stack");
rename("image1");

run("Duplicate...", "title=image2 duplicate stack");
run("Gaussian Blur...", "sigma=1 stack");

imageCalculator("Difference stack", "image1","image2");

selectWindow("image2");
close();

run("Gaussian Blur...", "sigma=10 stack");

setVoxelSize(pw, pw, pd, "um");

run("Fit Sphere", "");