package sunmisc.utils.concurrent;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sunmisc.utils.concurrent.maps.ConcurrentEnumMap;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Threads(1)
@Fork(1)
public class UnblockingEnumMap {
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(UnblockingEnumMap.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }

    @Param({"A", "N", "Z"})
    private Letter key;
    private ConcurrentMap<Letter, String> enumMap, hashMap;

    @Setup
    public void prepare() {
        hashMap = new ConcurrentHashMap<>();
        enumMap = new ConcurrentEnumMap<>(Letter.class);

        Letter[] val = Letter.values();
        for (int i = 0, n = val.length >> 1; i < n; ++i) {
            Letter x = val[i];
            hashMap.put(x, x.name());
            enumMap.put(x, x.name());
        }
    }

    @Benchmark
    public Map.Entry<Letter, String> enumMapIterator() {
        Iterator<Map.Entry<Letter, String>> iterator
                = enumMap.entrySet().iterator();
        Map.Entry<Letter, String> last = null;
        while (iterator.hasNext()) {
            last = iterator.next();
        }
        return last;
    }
    @Benchmark
    public Map.Entry<Letter, String> hashMapIterator() {
        Iterator<Map.Entry<Letter, String>> iterator
                = hashMap.entrySet().iterator();
        Map.Entry<Letter, String> last = null;
        while (iterator.hasNext()) {
            last = iterator.next();
        }
        return last;
    }

    @Benchmark public String putIfAbsentAsHash() {return hashMap.putIfAbsent(key, "Test-Fest");}
    @Benchmark public String putIfAbsentAsEnum() {return enumMap.putIfAbsent(key, "Test-Fest");}

    @Benchmark public String putAsHash() {return hashMap.put(key, "Test-Fest");}
    @Benchmark public String putAsEnum() {return enumMap.put(key, "Test-Fest");}

    @Benchmark public String removeAsHash() {return hashMap.remove(key);}
    @Benchmark public String removeAsEnum() {return enumMap.remove(key);}

    @Benchmark public boolean removeValAsHash() {return hashMap.remove(key, "T");}
    @Benchmark public boolean removeValAsEnum() {return enumMap.remove(key, "T");}

    @Benchmark public boolean replaceAsHash() {return hashMap.replace(key, "Q", "L");}
    @Benchmark public boolean replaceAsEnum() {return enumMap.replace(key, "Q", "L");}

    @Benchmark public String mergeAsHash() {return hashMap.merge(key, "Test-Fest", (k,v) -> "T");}
    @Benchmark public String mergeAsEnum() {return enumMap.merge(key, "Test-Fest", (k,v) -> "T");}

    @Benchmark public String computeAsHash() {return hashMap.compute(key, (k,v) -> "F");}
    @Benchmark public String computeAsEnum() {return enumMap.compute(key, (k,v) -> "F");}

    @Benchmark public String computeIfAbsentAsHash() {return hashMap.computeIfAbsent(key, (k) -> "Q");}
    @Benchmark public String computeIfAbsentAsEnum() {return enumMap.computeIfAbsent(key, (k) -> "Q");}

    @Benchmark public String computeIfPresentAsHash() {return hashMap.computeIfPresent(key, (k,v) -> "H");}
    @Benchmark public String computeIfPresentAsEnum() {return enumMap.computeIfPresent(key, (k,v) -> "H");}

    @Benchmark public String getAsHash() {return hashMap.get(key);}
    @Benchmark public String getAsEnum() {return enumMap.get(key);}

    @Benchmark public int clearAsHash() {hashMap.clear(); return 0;}
    @Benchmark public int clearAsEnum() {enumMap.clear(); return 0;}

    @Benchmark public int hashCodeHashMap() {return hashMap.hashCode();}
    @Benchmark public int hashCodeEnumMap() {return enumMap.hashCode();}


    @Benchmark public boolean equalsHashMap() {return hashMap.equals(enumMap);}
    @Benchmark public boolean equalsEnumMap() {return enumMap.equals(hashMap);}

    public enum Letter {
        A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z
    }
}