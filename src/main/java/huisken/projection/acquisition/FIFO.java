package huisken.projection.acquisition;

public class FIFO {

	private short[][] cache;
	private int first = 0;
	private int last = -1;
	private int size = 0;
	private final Object lock = new Object();

	public FIFO(int n, int nInner) {
		cache = new short[n][nInner];
	}

	public void add(short[] data) {
		synchronized(lock) {
			last = (last + 1) % cache.length;
			if(!isEmpty() && last == first) {
				throw new RuntimeException("Overwriting data");
			}
			System.arraycopy(data, 0, cache[last], 0, data.length);
			size++;
			lock.notifyAll();
		}
	}

	public void get(short[] ret) {
		synchronized(lock) {
			if(isEmpty()) {
				try {
					lock.wait();
				} catch(InterruptedException e) {
					e.printStackTrace();
				}
			}
			System.arraycopy(cache[first], 0, ret, 0, ret.length);
			first = (first + 1) % cache.length;
			size--;
		}
	}

	public int size() {
		return size;
	}

	public boolean isEmpty() {
		return size == 0;
	}

	public static void main(String[] args) {
		final FIFO cache = new FIFO(100, 1);

		new Thread() {
			public void run() {
				for(int i = 0; i < 100; i++) {
					cache.add(new short[] {(short)i});
				}
			}
		}.start();

		new Thread() {
			private short[] x = new short[1];
			public void run() {
				for(int i = 0; i < 100; i++) {
					cache.get(x);
				}
			}
		}.start();
	}
}

