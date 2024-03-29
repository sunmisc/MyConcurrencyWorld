package sunmisc.utils.concurrent;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.CommandLineOptionException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sunmisc.utils.concurrent.deque.UnblockingLinkedDeque;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 4, time = 1)
@Threads(Threads.MAX)
@Fork(1)
public class ConcurrentLinkedDequeBulk {

    private UnblockingLinkedDeque<Integer> concurrentLinkedProto;

    private ConcurrentLinkedDeque<Integer> deque;
    @Setup
    public void prepare() {
        concurrentLinkedProto = new UnblockingLinkedDeque<>();
        deque = new ConcurrentLinkedDeque<>();
    }

    @Benchmark
    public Integer myConcurrentLinkedList() {
        concurrentLinkedProto.add(1);
        concurrentLinkedProto.add(2);
        concurrentLinkedProto.add(3);
        concurrentLinkedProto.add(4);
        concurrentLinkedProto.add(5);

        concurrentLinkedProto.poll();
        concurrentLinkedProto.poll();
        concurrentLinkedProto.poll();
        concurrentLinkedProto.poll();
        concurrentLinkedProto.poll();
        return 232;

    }
    @Benchmark
    public boolean concurrentLinkedQ() {
        deque.add(1);
        deque.add(2);
        deque.add(3);
        deque.add(4);
        deque.add(5);

        deque.poll();
        deque.poll();
        deque.poll();
        deque.poll();
        deque.poll();
        return true;
    }
    public static void main(String[] args) throws RunnerException, CommandLineOptionException {
        Options opt = new OptionsBuilder()
                .include(ConcurrentLinkedDequeBulk.class.getSimpleName())
                .addProfiler(GCProfiler.class)
                .build();
        Runner runner = new Runner(opt);
        runner.run();
    }
}
