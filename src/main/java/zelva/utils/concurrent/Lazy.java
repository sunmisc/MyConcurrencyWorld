package zelva.utils.concurrent;

import java.util.Objects;
import java.util.function.Supplier;

public class Lazy<V> {
    static final Object NIL = new Object();
    /*
     * Perhaps, one of the most surprising JMM behaviors is that volatile fields do not include
     * the final field semantics. That is, if we publish the reference to the object racily,
     * then we can see the default value for the "volatile" field! This is mostly because the
     *
     * It can be seen on some platforms
     */
    @SuppressWarnings("unchecked")
    private volatile V value = (V) NIL;
    private final Supplier<V> supplier;

    public Lazy(Supplier<V> supplier) {
        this.supplier = Objects.requireNonNull(supplier);
    }
    public V get() {
        V val;
        if ((val = value) == NIL) {
            synchronized (this) {
                // no need for volatile-read here
                if ((val = value)
                        == NIL) {
                    return value = supplier.get();
                }
            }
        }
        return val;
    }
    public boolean isDone() {
        return value != NIL;
    }
}
