package org.simpleflatmapper.util;

import java.io.IOException;
import java.io.Reader;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.locks.LockSupport;


public class ParallelReader extends Reader {
    
    //IFJAVA8_START
    private static final Executor DEFAULT_EXECUTOR_J8 = ForkJoinPool.getCommonPoolParallelism() > 1 ? ForkJoinPool.commonPool() : new Executor() {
        public void execute(Runnable command) {
            (new Thread(command)).start();
        }
    };
    //IFJAVA8_END
    private static Executor DEFAULT_EXECUTOR_J6 = null;
    
    private static final Object lock = new Object();
    
    public static Executor getDefaultExecutor() {
        //IFJAVA8_START
        if (true) {
            return DEFAULT_EXECUTOR_J8;
        }
        //IFJAVA8_END

        synchronized (lock) {
            if (DEFAULT_EXECUTOR_J6 == null) {
                DEFAULT_EXECUTOR_J6 = newDefaultExecutor();
            }
        }
        return DEFAULT_EXECUTOR_J6;
    }

    private static Executor newDefaultExecutor() {
        int p = Runtime.getRuntime().availableProcessors();
        if (p <= 1) {
            return new Executor() {
                public void execute(Runnable command) {
                    (new Thread(command)).start();
                }
            };
        } else {
            return Executors.newScheduledThreadPool(Math.min(p, 0x7fff));
        }


    } 
    
    private static final WaitingStrategy DEFAULT_WAITING_STRATEGY = new WaitingStrategy() {
        @Override
        public void idle() {
            LockSupport.parkNanos(1l);
        }
    };
    
    private static final int DEFAULT_READ_BUFFER_SIZE = 8192;
    private static final int DEFAULT_RING_BUFFER_SIZE = 1024 * 64; // 64 k
    
    private final RingBufferReader reader;

    public ParallelReader(Reader reader) {
        this(reader, getDefaultExecutor(), DEFAULT_RING_BUFFER_SIZE);
    }

    public ParallelReader(Reader reader, Executor executorService) {
        this(reader, executorService, DEFAULT_RING_BUFFER_SIZE);
    }

    public ParallelReader(Reader reader, Executor executorService, int bufferSize) {
        this(reader, executorService, bufferSize, DEFAULT_READ_BUFFER_SIZE);
    }
    
    public ParallelReader(Reader reader, Executor executorService, int bufferSize, int readBufferSize) {
        this(reader, executorService, bufferSize, readBufferSize, DEFAULT_WAITING_STRATEGY);
    }

    public ParallelReader(Reader reader, Executor executorService, int bufferSize, int readBufferSize, WaitingStrategy waitingStrategy) {
        this.reader = new RingBufferReader(reader, executorService, bufferSize, readBufferSize, waitingStrategy);
    }

    @Override
    public int read() throws IOException {
        return reader.read();
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        return reader.read(cbuf, off, len);
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
    
    public interface WaitingStrategy {
        void idle();
    }
}


class Pad0 {
    long p1,p2,p3,p4,p5,p6,p7;
}
class Tail extends Pad0 {
    volatile long tail = 0;
}
class Pad1 extends Tail { long p1,p2,p3,p4,p5,p6,p7; }
class Buffer extends Pad1 { char[] buffer; }
class Pad2 extends Buffer { long p1,p2,p3,p4,p5,p6,p7; }
class Head extends Pad2 {
    volatile long head = 0;
}

final class RingBufferReader extends Head {

    public static final int L1_CACHE_LINE_SIZE = 64;
    private final Reader reader;
    private final DataProducer dataProducer;
    
    private final long bufferMask;

    private final long capacity;
    private final long tailPadding;
    private long tailCache;
    private long headCache;
    private final ParallelReader.WaitingStrategy waitingStrategy;

    public RingBufferReader(Reader reader, Executor executorService, int ringBufferSize, int readBufferSize, ParallelReader.WaitingStrategy waitingStrategy) {
        int powerOf2 =  1 << 32 - Integer.numberOfLeadingZeros(ringBufferSize - 1);
        tailPadding = powerOf2 <= 1024 ? 0 : L1_CACHE_LINE_SIZE;
        this.reader = reader;
        buffer = new char[powerOf2];
        bufferMask = buffer.length - 1;
        this.waitingStrategy = waitingStrategy;
        capacity = buffer.length;
        dataProducer = new DataProducer(readBufferSize);
        executorService.execute(dataProducer);
    }

    public int read(char[] cbuf, int off, int len) throws IOException {
        final long currentHead = head;
        do {
            if (currentHead < tailCache) {
                int l = read(cbuf, off, len, currentHead, tailCache);

                head = currentHead + l;
                return l;
            }

            tailCache = tail;
            if (currentHead >= tailCache) {
                if (!dataProducer.run) {
                    if (dataProducer.exception != null) {
                        throw dataProducer.exception;
                    }
                    tailCache = tail;
                    if (currentHead >= tailCache) {
                        return -1;
                    }
                }
                waitingStrategy.idle();
            }
        } while(true);
    }

    public int read() throws IOException {

        final long currentHead = head;
        do {
            if (currentHead < tailCache) {

                int headIndex = (int) (currentHead & bufferMask);

                char c = buffer[headIndex];

                head = currentHead + 1;

                return c;
            }

            tailCache = tail;
            if (currentHead >= tailCache) {
                if (!dataProducer.run) {
                    if (dataProducer.exception != null) {
                        throw dataProducer.exception;
                    }
                    tailCache = tail;
                    if (currentHead >= tailCache) {
                        return -1;
                    }
                }
                waitingStrategy.idle();
            }
        } while(true);
    }

    private int read(char[] cbuf, int off, int len, long currentHead, long currentTail) {

        int headIndex = (int) (currentHead & bufferMask);
        int usedLength = (int) (currentTail - currentHead);

        int block1Length = Math.min(len, Math.min(usedLength, (int) (capacity - headIndex)));
        int block2Length =  Math.min(len, usedLength) - block1Length;

        System.arraycopy(buffer, headIndex, cbuf, off, block1Length);
        System.arraycopy(buffer, 0, cbuf, off+ block1Length, block2Length);

        return block1Length + block2Length;
    }

    public void close() throws IOException {
        dataProducer.stop();
        reader.close();
    }

    private final class DataProducer implements Runnable {
        private volatile boolean run = true;
        private volatile IOException exception;
        
        private char[] _buffer;
        private int size;
        private int offset;

        public DataProducer(int bufferSize) {
            _buffer = new char[bufferSize];
        }

        @Override
        public void run() {
            long currentTail = tail;
            while(run) {

                final long wrapPoint = currentTail - buffer.length + tailPadding;

                if (headCache <= wrapPoint) {
                    headCache = head;
                    if (headCache <= wrapPoint) {
                        waitingStrategy.idle();
                        continue;
                    }
                }

                try {
                    int r =  read(currentTail, headCache);
                    if (r == -1) {
                        run = false;
                    } else {
                        currentTail += r;
                        tail = currentTail;
                    }
                } catch (IOException e) {
                    exception = e;
                    run = false;
                }
            }
        }

        private int read(long currentTail, long currentHead) throws IOException {
            long used = currentTail - currentHead;

            long length = capacity - used - tailPadding;
            
            int tailIndex = (int) (currentTail & bufferMask);

            int endBlock1 = (int) Math.min(tailIndex + length,  capacity );

            int block1Length = endBlock1 - tailIndex;
            
            if (offset >= size) { // no more to read in the buffer
                int l = reader.read(_buffer, 0, _buffer.length);
                if (l == -1) return -1; // end of buffer
                size = l;
                offset = 0;
            }
            
            int l = Math.min(block1Length, size - offset);
            System.arraycopy(_buffer, offset, buffer, tailIndex, l);
            offset += l;
            return l;
        }

        public void stop() {
            run = false;
        }
    }
}


