package sunmisc.utils.concurrent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class ConcurrentLinkedHashMap<K,V> extends AbstractMap<K,V>
        implements ConcurrentMap<K,V> {
    static final int MAXIMUM_CAPACITY = 1 << 30;
    private final Node<K,V> head;
    private volatile Node<K,V> tail;
    private final AtomicInteger size = new AtomicInteger();

    // todo: UnblockingArrayBuffer
    private volatile Bucket<K,V>[] table = new Bucket[8];

    public ConcurrentLinkedHashMap() {
        head = tail = new DummyNode<>();
    }

    public static void main(String[] args) {
        ConcurrentLinkedHashMap<Integer,Integer> map
                = new ConcurrentLinkedHashMap<>();

        for (int i = 0; i < 4; ++i) {
            map.put(i,i);
        }
        map.pollFirst();

        System.out.println(Arrays.toString(map.table));
        System.out.println(map);
    }

    public void pollFirst() {
        for (Node<K,V> h = head; h != null; h = h.next) {
            if (!h.isDead() && remove(h.getKey()) != null)
                return;
        }

    }

    static int spread(int h) {
        return (h ^ (h >>> 16));
    }

    @Override
    public int size() {
        return size.get();
    }

    @Override
    public V get(Object key) {
        final int h = spread(key.hashCode());
        Bucket<K,V>[] tab = table;
        int i = h & (tab.length - 1);
        Bucket<K,V> n = tabAt(tab, i);
        if (n == null)
            return null;
        Node<K,V> x = n.find((K)key);
        return x == null ? null : x.getValue();
    }

    public K getFirstKey() {
        Node<K,V> x = tryFindNextActiveNode(head);
        return x.getKey();
    }
    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ",
                "[", "]");
        for (Node<K,V> h = head; h != null; h = h.next) {
            joiner.add(h.toString());
        }
        return joiner.toString();
    }


    private V putVal(K key, V value, boolean ifAbsent) {
        final int h = spread(key.hashCode());
        Bucket<K, V>[] tab = table;
        int n = h & (tab.length - 1);

        Bucket<K, V> x = tabAt(tab, n);
        if (x == null) {

            Bucket<K, V> bucket = newBin(key);
            Node<K, V> newNode = new HashNode<>(h, key, value);
            bucket.addIfAbsent(newNode);

            if ((x = caeTabAt(tab, n, null, bucket)) == null) {
                linkLast(newNode);
                return null;
            }
        }
        x.lock();
        try {
            HashNode<K, V> e = new HashNode<>(h, key, value);
            Node<K, V> q = x.addIfAbsent(e);

            if (q != null) {
                return ifAbsent ? q.getValue() : q.setValue(value);
            } else {
                linkLast(e);

                return value;
            }
        } finally {
            x.unlock();
        }
    }
    @Nullable
    @Override
    public V put(K key, V value) {
        return putVal(key, value, false);
    }
    @Override
    public boolean replace(@NotNull K key,
                           @NotNull V oldValue,
                           @NotNull V newValue) {
        final int h = spread(key.hashCode());
        Bucket<K,V>[] tab = table;
        int n = h & (tab.length - 1);
        Bucket<K,V> x = tabAt(tab, n);


        if (x == null) {
            return false;
        } else {
            x.lock();
            try {
                Node<K, V> e = x.find(key);

                if (Objects.equals(e.getValue(), oldValue)) {
                    e.setValue(newValue);
                    return true;
                } else
                    return false;
            } finally {
                x.unlock();
            }
        }
    }
    private V removeVal(K key, V expected) {
        final int h = spread(key.hashCode());
        Bucket<K,V>[] tab = table;

        int n = h & (tab.length - 1);

        Bucket<K,V> f = tabAt(tab, n);

        if (f == null)
            return null;
        else {
            f.lock();
            try {
                Node<K, V> x = expected == null
                        ? f.remove(key)
                        : f.remove(key, expected);

                if (x == null)
                    return null;
                else if (f.size() == 0)
                    casTabAt(tab, n, f, null);

                V val = x.setValue(null); // mark

                unlink(x);

                return val;
            } finally {
                f.lock();
            }
        }
    }

    @Override
    public V remove(Object key) {
        return removeVal((K) key, null);
    }

    private void linkLast(Node<K,V> newNode) {
        for (;;) {
            Node<K,V> l = tail;

            for (;;) {
                Node<K,V> x = l.next;
                if (x != null)
                    l = x;
                else
                    break;
            }

            PREV.set(newNode, l);

            if (NEXT.weakCompareAndSet(l, null, newNode)) {
                TAIL.compareAndSet(this, l, newNode);

                size.getAndIncrement();
                return;
            }
        }
    }
    private void unlink(Node<K,V> x) {
        Node<K,V> prev = x.prev, next = x.next;

        if (prev != null) {
            Node<K,V> activePrev
                    = tryFindPrevActiveNode(prev);
            skipDeletedSuccessors(activePrev);

            if (next == null)
                TAIL.compareAndSet(this, x, activePrev);
        }
        if (next != null) {
            Node<K,V> activeNext
                    = tryFindNextActiveNode(next);

            skipDeletedPredecessors(activeNext);
        }
    }

    private void skipDeletedPredecessors(Node<K,V> x) {
        Node<K,V> p = x.prev,
                n = tryFindPrevActiveNode(p);
        if (p != n)
            PREV.setRelease(x, n);
    }

    private void skipDeletedSuccessors(Node<K,V> x) {
        Node<K, V> p = x.next,
                n = tryFindNextActiveNode(p);
        if (p != n)
            NEXT.setRelease(x, n);
    }
    Node<K,V> tryFindNextActiveNode(Node<K,V> src) {
        Node<K,V> n = src, p;
        while (n.isDead() && (p = n.next) != null)
            n = p;
        return n;
    }

    Node<K,V> tryFindPrevActiveNode(Node<K,V> src) {
        Node<K,V> n = src, p;
        while (n.isDead() && (p = n.prev) != null)
            n = p;
        return n;
    }

    @NotNull
    @Override
    public Set<Entry<K, V>> entrySet() {
        return null;
    }

    @Override
    public V putIfAbsent(@NotNull K key, V value) {
        return putVal(key, value, true);
    }

    @Override
    public boolean remove(@NotNull Object key, Object value) {
        return removeVal((K) key, (V) value) == value;
    }


    @Override
    public V replace(@NotNull K key, @NotNull V value) {
        return put(key, value);
    }

    static class HashNode<K,V> extends Node<K,V> {
        final int hash;
        final K key;
        volatile V value;

        HashNode(int hash, K key, V value) {
            this.hash = hash;
            this.key = key;
            this.value = value;
        }

        @Override public K getKey() { return key; }

        @Override public V getValue() { return value; }

        @Override
        public V setValue(V val) {
            V v = value; // plain-read
            value = val; // release
            return v;
        }
    }

    static abstract class Node<K,V> implements Map.Entry<K,V> {
        volatile Node<K,V> prev, next;

        boolean isDead() {
            return getValue() == null;
        }

        @Override
        public final String toString() {
            return getKey() + "=" + getValue();
        }
        @Override
        public V setValue(V value) {
            throw new UnsupportedOperationException();
        }
        @Override
        public int hashCode() {
            return Objects.hashCode(getKey()) ^ Objects.hashCode(getValue());
        }
        @Override
        public final boolean equals(Object o) {
            Object k, v, u; Map.Entry<?,?> e;

            K key = getKey();
            V val = getValue();
            return ((o instanceof Map.Entry) &&
                    (k = (e = (Map.Entry<?,?>)o).getKey()) != null &&
                    (v = e.getValue()) != null &&
                    (k == key || k.equals(key)) &&
                    (v == (u = val) || v.equals(u)));
        }
    }
    /*
     * Atomic access methods are used for array
     */
    private static <K,V> Bucket<K,V>
    tabAt(Bucket<K,V>[] tab, int i) {
        return (Bucket<K, V>) AA.getAcquire(tab, i);
    }
    private static <K,V> Bucket<K,V>
    caeTabAt(Bucket<K,V>[] tab, int i,
             Bucket<K,V> c,
             Bucket<K,V> v) {
        return (Bucket<K, V>) AA.compareAndExchange(tab, i, c, v);
    }
    private static <K,V> boolean
    casTabAt(Bucket<K,V>[] tab, int i,
             Bucket<K,V> c,
             Bucket<K,V> v) {
        return AA.compareAndSet(tab, i, c, v);
    }
    private static <K,V> Bucket<K,V>
    getAndSetAt(Bucket<K,V>[] tab, int i, Bucket<K,V> v) {
        return (Bucket<K, V>) AA.getAndSet(tab, i, v);
    }

    // VarHandle mechanics
    private static final VarHandle TAIL;

    private static final VarHandle PREV, NEXT;

    private static final VarHandle AA
            = MethodHandles.arrayElementVarHandle(Bucket[].class);

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            TAIL = l.findVarHandle(ConcurrentLinkedHashMap.class, "tail",
                    Node.class);

            NEXT = l.findVarHandle(Node.class, "next",
                    Node.class);
            PREV = l.findVarHandle(Node.class, "prev",
                    Node.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static <K,V> AbstractBucket<K,V> newBin(K key) {
        return key instanceof Comparable<?>
                ? new TreeBucket<>()
                : new LinkedBucket<>();
    }

    // todo: need volatile for safe reading from bucket
    private static class TreeBucket<K,V> extends AbstractBucket<K,V> {

        private final TreeMap<K, Node<K,V>> tree
                = new TreeMap<>();


        @Override
        public Node<K,V> addIfAbsent(Node<K, V> n) {
            return tree.putIfAbsent(n.getKey(), n);
        }

        @Override
        public Node<K, V> remove(K key) {
            return tree.remove(key);
        }

        @Override
        public Node<K, V> remove(K key, V expected) {
            AtomicReference<Node<K,V>> ref = new AtomicReference<>();
            tree.computeIfPresent(key,
                    (k,v) -> {

                        if (Objects.equals(v.getValue(), expected)) {
                            ref.setPlain(v);
                            return null;
                        }
                        return v;
                    });
            return ref.getPlain();
        }

        @Override
        public Node<K, V> find(K key) {
            return tree.get(key);
        }

        @Override
        public int size() {
            return tree.size();
        }

        @NotNull
        @Override
        public Iterator<Node<K, V>> iterator() {
            return tree.values().iterator();
        }
        @Override
        public String toString() {
            return tree.values().toString();
        }
    }

    private static class LinkedBucket<K,V> extends AbstractBucket<K,V> {
        private final LinkedList<Node<K,V>> list
                = new LinkedList<>(); // todo:

        @Override
        public Node<K,V> addIfAbsent(Node<K, V> n) {
            Node<K,V> f = find(n.getKey());

            if (f != null)
                return f;
            list.add(n);
            return null;
        }

        @Override
        public Node<K,V> remove(K key) {
            Iterator<Node<K,V>> itr = iterator();
            while (itr.hasNext()) {
                Node<K,V> x = itr.next();
                if (Objects.equals(x.getKey(), key)) {
                    itr.remove();
                    return x;
                }
            }
            return null;
        }

        @Override
        public Node<K, V> remove(K key, V expected) {
            Iterator<Node<K,V>> itr = iterator();
            while (itr.hasNext()) {
                Node<K,V> x = itr.next();
                if (Objects.equals(x.getKey(), key) &&
                        Objects.equals(x.getValue(), expected)) {
                    itr.remove();
                    return x;
                }
            }
            return null;
        }

        @Override
        public Node<K, V> find(K key) {

            for (Node<K,V> n : list) {
                if (Objects.equals(n.getKey(), key)) {
                    return n;
                }
            }
            return null;
        }

        @Override
        public int size() {
            return list.size();
        }

        @NotNull
        @Override
        public Iterator<Node<K, V>> iterator() {
            return list.iterator();
        }

        @Override
        public String toString() {
            return list.toString();
        }
    }


    private abstract static class AbstractBucket<K,V>
            extends ReentrantLock
            implements Bucket<K,V> {
    }

    private static class DummyNode<K,V> extends Node<K,V> {


        @Override
        public K getKey() {
            return null;
        }

        @Override
        public V getValue() {
            return null;
        }

        @Override
        boolean isDead() {
            return true;
        }
    }

    private interface Bucket<K,V>
            extends Iterable<Node<K,V>>, Lock {
        Node<K,V> addIfAbsent(Node<K,V> n);

        Node<K,V> remove(K key);

        Node<K,V> remove(K key, V expected);

        Node<K,V> find(K key);

        int size();
    }
}